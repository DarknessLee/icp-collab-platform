import { test, expect } from '@playwright/test';
import { apiGet, apiLogin, expectNoIssues, paginationTotal, uiLogin, watchPage } from './helpers';

interface PageResult<T> {
  total: number;
  list: T[];
}

interface OperLog {
  traceId?: string;
  operName?: string;
}

test.describe('E14 审计', () => {
  test('E14 admin 操作/登录/变更日志可筛选，traceId 详情可追', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('admin');
    const oper = await apiGet<PageResult<OperLog>>('/logs/oper?pageNum=1&pageSize=10', auth.token);
    const operFiltered = await apiGet<PageResult<OperLog>>(
      '/logs/oper?pageNum=1&pageSize=10&operName=admin',
      auth.token,
    );
    const loginLog = await apiGet<PageResult<unknown>>('/logs/login?pageNum=1&pageSize=10', auth.token);
    const changes = await apiGet<PageResult<unknown>>('/logs/changes?pageNum=1&pageSize=10', auth.token);

    expect(oper.total, '操作日志应有数据').toBeGreaterThan(0);
    expect(loginLog.total, '登录日志应有数据').toBeGreaterThan(0);
    expect(operFiltered.total, '操作人筛选应命中').toBeGreaterThan(0);

    const traceId = oper.list.find((row) => row.traceId)?.traceId;
    expect(traceId, '操作日志应带 traceId').toBeTruthy();
    const trace = await apiGet<unknown[]>(`/logs/trace/${traceId}`, auth.token);
    expect(Array.isArray(trace), 'trace 接口返回数组').toBeTruthy();
    expect(trace.length, 'trace 链路至少 1 条').toBeGreaterThan(0);

    await uiLogin(page, 'admin');
    await page.goto('/system/log');
    await expect(page.locator('.page-toolbar-heading').filter({ hasText: '审计日志' }).first()).toBeVisible();

    const tabs = page.locator('.el-tabs__item');
    await expect(tabs).toHaveCount(3);
    await expect(tabs.filter({ hasText: '操作日志' })).toBeVisible();
    await expect(tabs.filter({ hasText: '登录日志' })).toBeVisible();
    await expect(tabs.filter({ hasText: '变更日志' })).toBeVisible();

    await expect(page.locator('.el-table__row').first()).toBeVisible();
    // 页面与接口同源；UI 登录本身会新增一条登录/操作日志，比较前后两次快照
    const operAfterLoad = await apiGet<PageResult<OperLog>>('/logs/oper?pageNum=1&pageSize=10', auth.token);
    const pageOperTotal = await paginationTotal(page);
    expect([oper.total, operAfterLoad.total], '操作日志总数与接口一致').toContain(pageOperTotal);

    // 操作人筛选：契约参数 operName，筛选后每行操作人都是 admin
    await page.locator('input[placeholder="操作人"]').fill('admin');
    await page.locator('button:has-text("查询")').first().click();
    await page.waitForTimeout(800);
    const operFilteredAfter = await apiGet<PageResult<OperLog>>(
      '/logs/oper?pageNum=1&pageSize=10&operName=admin',
      auth.token,
    );
    expect(operFilteredAfter.total, '操作人筛选应命中').toBeGreaterThan(0);
    expect(operFilteredAfter.total, '操作人筛选应缩小结果集').toBeLessThan(operAfterLoad.total);
    expect(await paginationTotal(page), '筛选后总数与接口一致').toBe(operFilteredAfter.total);
    const operNames = await page.locator('.el-table__row td:nth-child(1)').allInnerTexts();
    expect(operNames.length).toBeGreaterThan(0);
    // 后端 operName 为 like 模糊匹配，故断言每行都包含筛选词
    expect(
      operNames.every((name) => name.trim().toLowerCase().includes('admin')),
      '筛选后每行操作人均包含 admin',
    ).toBeTruthy();

    // 详情抽屉展示 traceId，且可复制
    await page.locator('.el-table__row').first().locator('button:has-text("详情")').click();
    const drawer = page.locator('.el-drawer:visible').first();
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText('操作日志详情');
    await expect(drawer).toContainText('TraceId');
    const drawerText = await drawer.innerText();
    expect(drawerText).toMatch(/[0-9a-f]{16,}/);
    await page.keyboard.press('Escape');

    await tabs.filter({ hasText: '登录日志' }).click();
    await page.waitForTimeout(600);
    await expect(page.locator('.el-table__row').first()).toBeVisible();
    const loginAfter = await apiGet<PageResult<unknown>>('/logs/login?pageNum=1&pageSize=10', auth.token);
    expect([loginLog.total, loginAfter.total], '登录日志总数与接口一致').toContain(
      await paginationTotal(page),
    );

    // 用户名筛选：契约参数 username，筛选结果 ⊆ 全量
    const loginFiltered = await apiGet<PageResult<{ username?: string }>>(
      '/logs/login?pageNum=1&pageSize=10&username=admin',
      auth.token,
    );
    await page.locator('input[placeholder="用户名"]').fill('admin');
    await page.locator('button:has-text("查询")').first().click();
    await page.waitForTimeout(800);
    expect(loginFiltered.total, '用户名筛选应命中').toBeGreaterThan(0);
    expect(loginFiltered.total, '用户名筛选结果 ⊆ 全量').toBeLessThanOrEqual(loginAfter.total);
    expect(await paginationTotal(page), '登录日志筛选后总数与接口一致').toBe(loginFiltered.total);
    const loginNames = await page.locator('.el-table__row td:nth-child(1)').allInnerTexts();
    expect(loginNames.length).toBeGreaterThan(0);
    expect(
      loginNames.every((name) => name.trim().toLowerCase().includes('admin')),
      '筛选后每行用户名均包含 admin',
    ).toBeTruthy();

    await tabs.filter({ hasText: '变更日志' }).click();
    await page.waitForTimeout(600);
    if (changes.total > 0) {
      expect(await paginationTotal(page), '变更日志总数与接口一致').toBe(changes.total);
    } else {
      await expect(page.locator('.el-table__empty-text')).toBeVisible();
    }

    expectNoIssues(issues, 'E14-admin');
  });

  test('E14 auditor 仅审计只读：可访问日志页，API 可读，无系统管理写页面', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('auditor');
    expect(auth.permissions).toContain('audit:log:view');
    // auditor 仅有日志只读权限（system:log:oper/login/change），不得有系统管理写权限
    expect(auth.permissions).not.toContain('system:user:add');
    expect(auth.permissions).not.toContain('system:role:add');
    expect(auth.permissions).not.toContain('system:menu:add');

    const oper = await apiGet<PageResult<OperLog>>('/logs/oper?pageNum=1&pageSize=5', auth.token);
    expect(oper.total).toBeGreaterThan(0);

    await uiLogin(page, 'auditor');
    await page.goto('/system/log');
    await expect(page.locator('.page-toolbar-heading').filter({ hasText: '审计日志' }).first()).toBeVisible();
    await expect(page.locator('.el-tabs__item')).toHaveCount(3);
    await expect(page.locator('.el-table__row').first()).toBeVisible();

    await page.goto('/system/user');
    await expect(page).toHaveURL(/\/403/);

    expectNoIssues(issues, 'E14-auditor');
  });
});
