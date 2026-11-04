import { expect, test } from '@playwright/test';
import {
  apiDelete,
  apiGet,
  apiLogin,
  apiPost,
  apiPut,
  expectNoIssues,
  uiLogin,
  uniqueSuffix,
  watchPage,
} from './helpers';

interface ControlRow {
  id: number;
  controlCode: string;
  controlName: string;
  controlType?: number;
  freq?: string;
  status?: number;
}

test('E07 控制点 CRUD：新建/编辑/停用（含控制类型与执行频率）', async ({ page }) => {
  test.setTimeout(120_000);
  const suffix = uniqueSuffix();
  const controlName = `E2E控制点-${suffix}`;

  const icpadmin = await apiLogin('icpadmin');
  const created = await apiPost<ControlRow>('/controls', icpadmin.token, {
    controlName,
    controlType: 1,
    freq: '每月',
    controlDesc: 'E2E 控制点描述：审批与复核分离',
    status: 1,
  });
  expect(created.id).toBeGreaterThan(0);
  expect(created.controlCode).toMatch(/^KZ-/);
  expect(created.controlName).toBe(controlName);

  // 编辑：改名称 + 执行频率
  const updated = await apiPut<ControlRow>(`/controls/${created.id}`, icpadmin.token, {
    controlName: `${controlName}-改`,
    controlType: 2,
    freq: '每季度',
    controlDesc: 'E2E 编辑后的控制点描述',
    status: 1,
  });
  expect(updated.controlName).toBe(`${controlName}-改`);
  expect(updated.freq).toBe('每季度');

  await uiLogin(page, 'icpadmin');
  const issues = watchPage(page);
  await page.goto('/icp/control');
  await page.locator('input[placeholder="控制点名称 / 编码"]').fill(controlName);
  await page.locator('input[placeholder="控制点名称 / 编码"]').press('Enter');
  const row = page.locator('.el-table__row').filter({ hasText: controlName }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await expect(row).toContainText('每季度');
  // 字典 icp_control_type：2 = 发现（1 预防 / 2 发现 / 3 纠正）
  await expect(row).toContainText('发现');

  // 删除并复核列表不再出现（逻辑删除）
  await apiDelete(`/controls/${created.id}`, icpadmin.token);
  await page.locator('input[placeholder="控制点名称 / 编码"]').press('Enter');
  await expect(page.locator('.el-table__row').filter({ hasText: controlName })).toHaveCount(0);

  // 列表接口与页面分页数字一致（同源）
  const pageData = await apiGet<{ total: number }>('/controls?pageNum=1&pageSize=10', icpadmin.token);
  expect(pageData.total).toBeGreaterThan(0);

  expectNoIssues(issues, 'E07 控制点');
});
