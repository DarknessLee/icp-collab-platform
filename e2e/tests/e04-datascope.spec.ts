import { test, expect } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiRaw,
  expectNoIssues,
  paginationTotal,
  uiLogin,
  watchPage,
} from './helpers';

interface PageResult<T> {
  total: number;
  list: T[];
}

test.describe('E04 数据权限', () => {
  test('E04 dept01 制度6/风险6/缺陷4：页面分页总数与 API total 一致', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('dept01');

    const policies = await apiGet<PageResult<unknown>>('/policies?pageNum=1&pageSize=1', auth.token);
    const risks = await apiGet<PageResult<unknown>>('/risks?pageNum=1&pageSize=1', auth.token);
    const defects = await apiGet<PageResult<unknown>>('/defects?pageNum=1&pageSize=1', auth.token);

    // 文档口径：DEPT_MANAGER 本部门数据
    expect(policies.total, 'dept01 制度数').toBe(6);
    expect(risks.total, 'dept01 风险数').toBe(6);
    expect(defects.total, 'dept01 缺陷数').toBe(4);

    await uiLogin(page, 'dept01');
    const checks: Array<[string, string, number]> = [
      ['/icp/policy', '制度库', policies.total],
      ['/icp/risk', '风险库', risks.total],
      ['/icp/defect', '缺陷台账', defects.total],
    ];
    for (const [path, heading, expected] of checks) {
      await page.goto(path);
      await expect(page.locator('.page-toolbar-heading').filter({ hasText: heading }).first()).toBeVisible();
      await expect(page).not.toHaveURL(/\/403/);
      expect(await paginationTotal(page), `${path} 页面总数`).toBe(expected);
    }

    expectNoIssues(issues, 'E04-dept01');
  });

  test('E04 staff01 制度2/缺陷2；/risks 返回 40300，页面跳 403', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('staff01');

    const policies = await apiGet<PageResult<unknown>>('/policies?pageNum=1&pageSize=1', auth.token);
    const defects = await apiGet<PageResult<unknown>>('/defects?pageNum=1&pageSize=1', auth.token);
    expect(policies.total, 'staff01 制度数').toBe(2);
    expect(defects.total, 'staff01 缺陷数').toBe(2);

    const riskRaw = await apiRaw('/risks?pageNum=1&pageSize=1', auth.token);
    expect(riskRaw.status, 'staff01 /risks HTTP 状态').toBe(403);
    expect(riskRaw.code, 'staff01 /risks 错误码').toBe(40300);

    await uiLogin(page, 'staff01');
    for (const [path, heading, expected] of [
      ['/icp/policy', '制度库', policies.total],
      ['/icp/defect', '缺陷台账', defects.total],
    ] as Array<[string, string, number]>) {
      await page.goto(path);
      await expect(page.locator('.page-toolbar-heading').filter({ hasText: heading }).first()).toBeVisible();
      expect(await paginationTotal(page), `${path} 页面总数`).toBe(expected);
    }

    await page.goto('/icp/risk');
    await page.waitForURL(/\/403/, { timeout: 20_000 });
    await expect(page.locator('.error-page')).toContainText('403');

    expectNoIssues(issues, 'E04-staff01');
  });
});
