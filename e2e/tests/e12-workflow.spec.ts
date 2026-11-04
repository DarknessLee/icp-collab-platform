import { test, expect } from '@playwright/test';
import { apiGet, apiLogin, expectNoIssues, uiLogin, watchPage } from './helpers';

interface PageResult<T> {
  total: number;
  list: T[];
}

interface MonitorRow {
  processInstanceId: number;
  title?: string;
  statusText?: string;
  status?: number;
  currentNode?: string;
}

interface WorkflowTaskRow {
  taskId: number;
  title?: string;
}

interface ProcessDef {
  processKey: string;
  processName?: string;
}

test.describe('E12 流程中心', () => {
  test('E12 admin 只读访问待办/已办/我发起/监控/实例/定义，数字与 API 一致', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('admin');
    const todo = await apiGet<PageResult<WorkflowTaskRow>>('/workflow/todo?pageNum=1&pageSize=10', auth.token);
    const done = await apiGet<PageResult<unknown>>('/workflow/done?pageNum=1&pageSize=10', auth.token);
    const initiated = await apiGet<PageResult<unknown>>('/workflow/initiated?pageNum=1&pageSize=10', auth.token);
    const monitor = await apiGet<
      PageResult<MonitorRow> & { running: number; completed: number; rejected: number; overdue: number }
    >('/workflow/monitor?pageNum=1&pageSize=10', auth.token);
    const processes = await apiGet<{ list: ProcessDef[] } | ProcessDef[]>('/workflow/processes', auth.token);
    const processList = Array.isArray(processes) ? processes : (processes.list ?? []);

    await uiLogin(page, 'admin');

    const pages: Array<[string, string]> = [
      ['/wf/todo', '我的待办'],
      ['/wf/done', '我的已办'],
      ['/wf/initiated', '我发起的'],
      ['/wf/monitor', '流程监控'],
      ['/wf/instance', '流程实例'],
      ['/wf/definition', '流程定义'],
    ];
    for (const [path, heading] of pages) {
      await page.goto(path);
      await expect(page.locator('.page-toolbar-heading').filter({ hasText: heading }).first()).toBeVisible();
      await expect(page).not.toHaveURL(/\/403/);
      const text = await page.locator('.page-container').first().innerText();
      expect(text, `${path} 不应出现 NaN`).not.toContain('NaN');
    }

    // 监控统计卡与接口同源
    await page.goto('/wf/monitor');
    await expect(page.locator('.page-toolbar-heading').filter({ hasText: '流程监控' }).first()).toBeVisible();
    const stats = page.locator('.wf-monitor__stat');
    await expect(stats).toHaveCount(4);
    await expect(stats.nth(0).locator('.wf-monitor__stat-value')).toHaveText(String(monitor.running));
    await expect(stats.nth(1).locator('.wf-monitor__stat-value')).toHaveText(String(monitor.completed));
    await expect(stats.nth(2).locator('.wf-monitor__stat-value')).toHaveText(String(monitor.rejected));
    await expect(stats.nth(3).locator('.wf-monitor__stat-value')).toHaveText(String(monitor.overdue));

    // 流程定义条数与接口一致（种子 6 条 BPMN）
    await page.goto('/wf/definition');
    const defRows = page.locator('.el-table__row');
    await expect(defRows).toHaveCount(processList.length);
    expect(processList.length).toBeGreaterThanOrEqual(6);

    // admin 只读：待办详情不应出现审批按钮
    if (todo.total > 0) {
      await page.goto('/wf/todo');
      await page.locator('.el-table__row').first().locator('button:has-text("处理")').click();
      const drawer = page.locator('.el-drawer:visible').first();
      await expect(drawer).toBeVisible();
      await expect(drawer.locator('button:has-text("同意")')).toHaveCount(0);
      await expect(drawer.locator('button:has-text("驳回")')).toHaveCount(0);
      await drawer.locator('.el-drawer__close-btn').click().catch(() => page.keyboard.press('Escape'));
    } else {
      test.info().annotations.push({ type: 'skip-note', description: 'clean seed 下 admin 无待办，审批按钮只读校验留待演示态' });
    }

    expectNoIssues(issues, 'E12');
  });

  test('E12 流程实例详情含表单/历史时间线/BPMN 图（运行中实例需高亮当前节点）', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('admin');
    const monitor = await apiGet<
      PageResult<MonitorRow> & { running: number }
    >('/workflow/monitor?pageNum=1&pageSize=50', auth.token);

    await uiLogin(page, 'admin');
    await page.goto('/wf/instance');

    if (monitor.total === 0) {
      test.info().annotations.push({
        type: 'skip-note',
        description: 'clean seed 下无流程实例；演示态（seed-flow）后再验 BPMN 高亮',
      });
      await expect(page.locator('.el-table__empty-text')).toBeVisible();
      expectNoIssues(issues, 'E12-instance');
      return;
    }

    await expect(page.locator('.el-table__row').first()).toBeVisible();
    await page.locator('.el-table__row').first().locator('button:has-text("查看")').click();
    const drawer = page.locator('.el-drawer:visible').first();
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText('表单信息');
    await expect(drawer).toContainText('流转历史');

    await drawer.locator('.el-tabs__item').filter({ hasText: '流转历史' }).click();
    await expect(drawer.locator('.el-timeline-item').first()).toBeVisible();

    await drawer.locator('.el-tabs__item').filter({ hasText: '流程图' }).click();
    await expect(drawer.locator('.wf-diagram svg[data-element-id]').first()).toBeVisible({ timeout: 20_000 });

    const running = monitor.list.some((row) => String(row.statusText ?? '').includes('运行中'));
    if (running || monitor.running > 0) {
      await expect(drawer.locator('.wf-diagram .wf-current').first()).toBeVisible({ timeout: 20_000 });
    } else {
      test.info().annotations.push({
        type: 'skip-note',
        description: '当前列表无运行中实例，BPMN 当前节点高亮未覆盖',
      });
    }

    expectNoIssues(issues, 'E12-instance');
  });
});
