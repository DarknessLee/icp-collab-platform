import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  expectNoIssues,
  findTodo,
  uiLogin,
  uniqueSuffix,
  watchPage,
} from './helpers';

interface MessageRow {
  id: number;
  title?: string;
  content?: string;
  bizType?: string;
  type?: number;
}

const today = new Date().toISOString().slice(0, 10);

test('E16 审批抄送：同意时选抄送人 → 对方消息中心出现「抄送」消息并按类型筛选', async ({ page }) => {
  test.setTimeout(120_000);
  const suffix = uniqueSuffix();
  const title = `E2E抄送采购-${suffix}`;

  const staff = await apiLogin('staff01');
  const auditor = await apiLogin('auditor');

  const created = await apiPost<{ id?: number; workflow?: { processInstanceId?: string } }>(
    '/workflow/purchase',
    staff.token,
    {
      title,
      amount: 888.88,
      budgetAmount: 2000,
      purchaseType: 2,
      supplier: 'E2E 供应商',
      purpose: `E2E 抄送验证 ${suffix}`,
      applyDate: today,
      submit: true,
    },
  );
  const instanceId = created.workflow?.processInstanceId;
  expect(instanceId).toBeTruthy();

  // 发起节点由发起人办理，同意时选择抄送人（审计员）
  const submitTodo = await findTodo(staff.token, { processInstanceId: instanceId });
  const approved = await apiPost<Record<string, unknown>>(
    `/workflow/tasks/${submitTodo.taskId}/approve`,
    staff.token,
    { comment: 'E2E 提交并抄送', ccUserIds: [auditor.userId] },
  );
  expect(approved).toBeTruthy();

  // API：抄送人收到 bizType=CC 的站内消息
  const ccPage = await apiGet<{ list?: MessageRow[] }>(
    '/messages?pageNum=1&pageSize=20&bizType=CC',
    auditor.token,
  );
  const cc = (ccPage.list ?? []).find((row) => (row.title ?? '').includes(title));
  expect(cc, '抄送人应收到本次流程的抄送消息').toBeTruthy();
  expect(cc!.content ?? '').toContain('审批通过');

  // UI：消息中心「业务筛选=流程抄送」可筛出该条消息
  await uiLogin(page, 'auditor');
  const issues = watchPage(page);
  await page.goto('/coll/message');
  await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 20_000 });
  await page.locator('.toolbar-select').nth(1).click();
  await page.locator('.el-select-dropdown__item', { hasText: '流程抄送' }).first().click();
  await page.waitForResponse(
    (res) => res.url().includes('/messages?') && res.url().includes('bizType=CC'),
    { timeout: 15_000 },
  );
  await expect(page.locator('.el-table__row').filter({ hasText: title }).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.locator('.el-table__row').filter({ hasText: '抄送' }).first()).toBeVisible();
  expectNoIssues(issues, 'E16 消息中心');
});
