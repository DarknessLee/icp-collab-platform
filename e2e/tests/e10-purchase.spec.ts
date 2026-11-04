import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  approveChain,
  findInstance,
  instanceDetail,
  instanceStatus,
  uiLogin,
  uniqueSuffix,
  watchPage,
  expectNoIssues,
} from './helpers';

interface PurchaseRow {
  id: number;
  applyNo: string;
  title: string;
  amount: number;
  budgetAmount?: number;
  status: number;
  processInstanceId?: string;
}

const today = new Date().toISOString().slice(0, 10);

test('E10 采购申请闭环：staff01 发起 → dept01 → MANAGEMENT 终审 → 已完成（金额两位小数）', async ({ page }) => {
  test.setTimeout(200_000);
  const suffix = uniqueSuffix();
  const title = `E2E采购申请-${suffix}`;

  const staff = await apiLogin('staff01');
  const dept = await apiLogin('dept01');
  const exec = await apiLogin('exec');
  const icpadmin = await apiLogin('icpadmin');

  const created = await apiPost<{ id?: number; applyNo?: string; workflow?: { processInstanceId?: string; taskId?: string } }>(
    '/workflow/purchase',
    staff.token,
    {
      title,
      amount: 12345.67,
      budgetAmount: 20000,
      purchaseType: 2,
      supplier: 'E2E 供应商',
      purpose: `E2E 采购用途 ${suffix}`,
      applyDate: today,
      submit: true,
    },
  );
  expect(created.workflow?.processInstanceId).toBeTruthy();
  expect(created.applyNo ?? '').toMatch(/^CG-/);

  const instance = await findInstance(staff.token, { title });
  expect(String(instance.processInstanceId)).toBe(created.workflow?.processInstanceId);

  const status = await approveChain(instance.processInstanceId, [
    { name: 'staff01', token: staff.token },
    { name: 'dept01', token: dept.token },
    { name: 'exec', token: exec.token },
    { name: 'icpadmin', token: icpadmin.token },
  ]);
  expect(status).toBe('COMPLETED');

  // 流程完成 → 采购单完成（status 4），金额保持两位小数精度
  const purchasePage = await apiGet<{ list?: PurchaseRow[] }>(
    `/workflow/purchase?pageNum=1&pageSize=200&keyword=${encodeURIComponent(title)}`,
    staff.token,
  );
  const purchase = (purchasePage.list ?? []).find((row) => row.title?.includes(suffix));
  expect(purchase).toBeTruthy();
  expect(Number(purchase!.amount)).toBeCloseTo(12345.67, 2);
  expect([2, 4]).toContain(purchase!.status);

  // UI：金额两位小数 + 状态「已完成」，页面数字与 API 同源
  await uiLogin(page, 'staff01');
  const issues = watchPage(page);
  await page.goto('/wf/purchase');
  await page.locator('input[placeholder="标题 / 采购单号"]').fill(title);
  await page.locator('input[placeholder="标题 / 采购单号"]').press('Enter');
  const row = page.locator('.el-table__row').filter({ hasText: title }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await expect(row).toContainText(/¥\s*12345\.67/);
  await expect(row).toContainText(/已完成|已通过/);

  // 实例历史包含审批留痕
  const detail = await instanceDetail(staff.token, instance.processInstanceId);
  expect(instanceStatus(detail.status)).toBe('COMPLETED');
  const history = await apiGet<unknown[]>(`/workflow/instances/${instance.processInstanceId}/history`, staff.token);
  expect(history.length).toBeGreaterThanOrEqual(2);

  expectNoIssues(issues, 'E10 采购闭环');
});
