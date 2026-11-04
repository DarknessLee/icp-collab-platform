import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  apiPostRaw,
  approveChain,
  findInstance,
  instanceDetail,
  instanceStatus,
  uiLogin,
  uniqueSuffix,
  waitInstanceStatus,
  waitToast,
  watchPage,
  expectNoIssues,
} from './helpers';

interface PolicyRow {
  id: number;
  policyCode: string;
  policyName: string;
  status: number;
  publishTime?: string;
  effectiveDate?: string;
}

interface CategoryNode {
  id: number;
  categoryName: string;
  children?: CategoryNode[];
}

function leafCategoryId(nodes: CategoryNode[]): number {
  for (const node of nodes) {
    if (node.children?.length) {
      const child = leafCategoryId(node.children);
      if (child) return child;
    }
    if (node.id) return node.id;
  }
  return 0;
}

const today = new Date().toISOString().slice(0, 10);

test('E05 制度闭环：icpadmin 新建→提交→审批通过（生效+publish_time）；admin 只读 403', async ({ page }) => {
  test.setTimeout(180_000);
  const suffix = uniqueSuffix();
  const policyName = `E2E制度闭环-${suffix}`;

  const icpadmin = await apiLogin('icpadmin');
  const admin = await apiLogin('admin');
  const exec = await apiLogin('exec');

  const categories = await apiGet<CategoryNode[]>('/policy-categories/tree', icpadmin.token);
  const categoryId = leafCategoryId(categories);
  expect(categoryId).toBeGreaterThan(0);

  // admin 对内控严格只读：POST /policies 必须 HTTP 403 + 40300
  const forbidden = await apiPostRaw('/policies', admin.token, {
    policyName: `E2E-admin-${suffix}`,
    categoryId,
    version: 'V1.0',
    effectiveDate: today,
    content: 'admin 不应创建成功',
  });
  expect(forbidden.status).toBe(403);
  expect(forbidden.code).toBe(40300);

  // icpadmin 新建制度（草稿）
  const created = await apiPost<PolicyRow>('/policies', icpadmin.token, {
    policyName,
    categoryId,
    version: 'V1.0',
    effectiveDate: today,
    content: `E2E 自动化制度内容 ${suffix}`,
    keywords: suffix,
  });
  expect(created.id).toBeGreaterThan(0);
  expect(created.policyCode).toMatch(/^ZD-/);
  expect(created.status).toBe(0);

  // UI：icpadmin 搜索到草稿并提交审批
  await uiLogin(page, 'icpadmin');
  const issues = watchPage(page);
  await page.goto('/icp/policy');
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').fill(policyName);
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').press('Enter');
  const row = page.locator('.el-table__row').filter({ hasText: policyName }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.locator('button:has-text("提交审批")').click();
  await page.locator('.el-message-box__btns button:has-text("确认")').click();
  await waitToast(page, '已提交审批');
  await page.waitForTimeout(1000);

  // 提交后进入审批中（后端状态机：0 草稿 → 1 审批中）
  const status = (await apiGet<PolicyRow>(`/policies/${created.id}`, icpadmin.token)).status;
  expect(status).not.toBe(0);

  // 流程：ICP_MANAGER → MANAGEMENT；按候选人链审批至结束
  const instance = await findInstance(icpadmin.token, { title: policyName });
  expect(instance.processKey ?? '').toContain('policy');
  const finalStatus = await approveChain(instance.processInstanceId, [
    { name: 'icpadmin', token: icpadmin.token },
    { name: 'exec', token: exec.token },
    { name: 'admin', token: admin.token },
  ]);
  expect(finalStatus).toBe('COMPLETED');

  // 审批通过后：制度生效 + publish_time 非空（由流程回调写入）
  const deadline = Date.now() + 20_000;
  let policy: PolicyRow | undefined;
  while (Date.now() < deadline) {
    policy = await apiGet<PolicyRow>(`/policies/${created.id}`, icpadmin.token);
    if (policy.status === 2 && policy.publishTime) break;
    await new Promise((resolve) => setTimeout(resolve, 600));
  }
  expect(policy?.status).toBe(2);
  expect(policy?.publishTime).toBeTruthy();

  // 页面复核：状态标签显示「已生效」，且 admin 页面无写操作按钮
  await page.reload();
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').fill(policyName);
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').press('Enter');
  const finalRow = page.locator('.el-table__row').filter({ hasText: policyName }).first();
  await expect(finalRow).toBeVisible({ timeout: 20_000 });
  await expect(finalRow).toContainText('已生效');

  await uiLogin(page, 'admin');
  await page.goto('/icp/policy');
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').fill(policyName);
  await page.locator('input[placeholder="制度名称 / 编码 / 关键字"]').press('Enter');
  const adminRow = page.locator('.el-table__row').filter({ hasText: policyName }).first();
  await expect(adminRow).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('button:has-text("新建制度")')).toHaveCount(0);

  // 流程实例详情可追踪（表单 + 历史）
  const detail = await instanceDetail(icpadmin.token, instance.processInstanceId);
  expect(instanceStatus(detail.status)).toBe('COMPLETED');
  expect(await waitInstanceStatus(icpadmin.token, instance.processInstanceId, 'COMPLETED')).toBe('COMPLETED');

  expectNoIssues(issues, 'E05 制度闭环');
});
