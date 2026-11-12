import { expect, test } from '@playwright/test';
import path from 'node:path';
import {
  API_BASE,
  apiGet,
  apiLogin,
  apiPost,
  apiPut,
  apiUpload,
  apiDelete,
  approveChain,
  expectNoIssues,
  uiLogin,
  uniqueSuffix,
  watchPage,
} from './helpers';

interface TaskRow {
  id: number;
  taskNo: string;
  taskTitle: string;
  status: number;
}

interface DocRow {
  id: number;
  docNo: string;
  docName: string;
  status: number;
}

interface CategoryNode {
  id: number;
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

const now = new Date();
const iso = (offsetDays: number) =>
  new Date(now.getTime() + offsetDays * 86_400_000).toISOString().slice(0, 10);
const isoTime = (offsetHours: number) =>
  new Date(now.getTime() + offsetHours * 3_600_000).toISOString().slice(0, 19).replace('T', ' ');

const EVIDENCE_FILE =
  process.env.E2E_EVIDENCE_FILE || path.resolve(process.cwd(), 'fixtures', 'evidence.pdf');

test('E13 协同运营：任务/文档权限下载/借阅审批归还/公告已读/消息 SSE/日程取消', async ({ page }) => {
  test.setTimeout(300_000);
  const suffix = uniqueSuffix();

  const staff = await apiLogin('staff01');
  const icpadmin = await apiLogin('icpadmin');
  const dept = await apiLogin('dept01');

  /* ---------- 任务闭环 ---------- */
  const taskTitle = `E2E协同任务-${suffix}`;
  const task = await apiPost<TaskRow>('/tasks', staff.token, {
    taskTitle,
    taskType: 2,
    priority: 1,
    assigneeId: staff.userId,
    planStart: iso(0),
    planEnd: iso(5),
    description: `E2E 协同任务 ${suffix}`,
  });
  expect(task.taskNo).toMatch(/^RW-/);
  await apiPost(`/tasks/${task.id}/start`, staff.token);
  await apiPost(`/tasks/${task.id}/complete`, staff.token, { comment: 'E2E 完成' });
  const taskDetail = await apiGet<TaskRow>(`/tasks/${task.id}`, staff.token);
  expect(taskDetail.status).toBe(2);

  /* ---------- 文档：上传→发布→授权→下载 ---------- */
  const docName = `E2E文档授权-${suffix}`;
  const categories = await apiGet<CategoryNode[]>('/doc-categories/tree', icpadmin.token);
  const categoryId = leafCategoryId(categories);
  expect(categoryId).toBeGreaterThan(0);
  const file = await apiUpload(EVIDENCE_FILE, icpadmin.token);
  const doc = await apiPost<DocRow>('/docs', icpadmin.token, {
    docName,
    categoryId,
    docType: 1,
    fileId: file.id,
    version: 'V1.0',
    description: `E2E 文档 ${suffix}`,
  });
  expect(doc.status).toBe(0);
  await apiPost(`/docs/${doc.id}/publish`, icpadmin.token);

  // 仅「查看」权限：下载必须 40300
  await apiPut(`/docs/${doc.id}/perms`, icpadmin.token, {
    perms: [{ targetType: 1, targetId: 4, permType: 1 }],
  });
  const denyRes = await fetch(`${API_BASE}/docs/${doc.id}/download`, {
    headers: { Authorization: `Bearer ${staff.token}` },
  });
  const denyBody = (await denyRes.json()) as { code?: number };
  expect(denyBody.code).toBe(40300);

  // 追加「下载」权限（同目标多行）→ 下载成功且为 PDF
  await apiPut(`/docs/${doc.id}/perms`, icpadmin.token, {
    perms: [
      { targetType: 1, targetId: 4, permType: 1 },
      { targetType: 1, targetId: 4, permType: 2 },
    ],
  });
  const allowRes = await fetch(`${API_BASE}/docs/${doc.id}/download`, {
    headers: { Authorization: `Bearer ${staff.token}` },
  });
  expect(allowRes.status).toBe(200);
  expect(allowRes.headers.get('content-type') ?? '').toContain('pdf');

  // 在线预览：已发布 PDF 文档在文档中心可直接内嵌打开
  await uiLogin(page, 'admin');
  await page.goto('/coll/doc');
  await page.locator('.toolbar-input input').first().fill(docName);
  await page.locator('.toolbar-input input').first().press('Enter');
  const previewRow = page.locator('.el-table__row').filter({ hasText: docName }).first();
  await expect(previewRow).toBeVisible({ timeout: 15_000 });
  await previewRow.locator('button', { hasText: '预览' }).click();
  await expect(page.locator('.doc-preview__frame')).toBeVisible({ timeout: 20_000 });
  await page.locator('.el-dialog__footer button', { hasText: '关闭' }).click();
  await expect(page.locator('.doc-preview')).toHaveCount(0);

  /* ---------- 借阅：申请→审批→归还 ---------- */
  const borrowReason = `E2E 借阅 ${suffix}`;
  const borrow = await apiPost<{ id: number; processInstanceId?: string; workflow?: { processInstanceId?: string } }>(
    '/borrows',
    staff.token,
    { docId: doc.id, reason: borrowReason, startDate: iso(0), endDate: iso(7) },
  );
  const borrowInstanceId = String(borrow.processInstanceId ?? borrow.workflow?.processInstanceId ?? '');
  expect(borrowInstanceId).toBeTruthy();
  const borrowStatus = await approveChain(borrowInstanceId, [
    { name: 'staff01', token: staff.token },
    { name: 'dept01', token: dept.token },
    { name: 'icpadmin', token: icpadmin.token },
  ]);
  expect(borrowStatus).toBe('COMPLETED');
  const approvedBorrow = await apiGet<{ status: number }>(`/borrows/${borrow.id}`, staff.token);
  expect(approvedBorrow.status).toBe(1);
  await apiPost(`/borrows/${borrow.id}/return`, staff.token);
  const returned = await apiGet<{ status: number }>(`/borrows/${borrow.id}`, staff.token);
  expect(returned.status).toBe(3);

  /* ---------- 公告：发布→已读幂等 ---------- */
  const noticeTitle = `E2E公告-${suffix}`;
  const notice = await apiPost<{ id: number }>('/notices', icpadmin.token, {
    noticeTitle,
    noticeType: 1,
    content: `E2E 公告内容 ${suffix}`,
    scope: 1,
  });
  await apiPost(`/notices/${notice.id}/publish`, icpadmin.token);
  const unreadBefore = await apiGet<{ unread: number }>('/notices/unread-count', staff.token);
  await apiPost(`/notices/${notice.id}/read`, staff.token);
  const unreadAfter1 = await apiGet<{ unread: number }>('/notices/unread-count', staff.token);
  expect(unreadAfter1.unread).toBe(Math.max(0, unreadBefore.unread - 1));
  await apiPost(`/notices/${notice.id}/read`, staff.token);
  const unreadAfter2 = await apiGet<{ unread: number }>('/notices/unread-count', staff.token);
  expect(unreadAfter2.unread).toBe(unreadAfter1.unread);

  /* ---------- 消息 SSE：后台发消息，页面实时收到 ---------- */
  await uiLogin(page, 'staff01');
  const issues = watchPage(page);
  await page.goto('/coll/message');
  await expect(page.locator('.page-toolbar-heading')).toContainText('消息中心');
  const messageTitle = `E2E实时消息-${suffix}`;
  const message = await apiPost<{ id: number }>('/messages', icpadmin.token, {
    receiverId: staff.userId,
    title: messageTitle,
    content: `E2E SSE 实时消息 ${suffix}`,
    messageType: 1,
  });
  const messageRow = page.locator('.el-table__row').filter({ hasText: messageTitle }).first();
  await expect(messageRow).toBeVisible({ timeout: 20_000 });
  await apiPost('/messages/read', staff.token, { ids: [message.id] });
  const unreadMessages = await apiGet<{ unread: number } | number>('/messages/unread-count', staff.token);
  const unreadValue = typeof unreadMessages === 'number' ? unreadMessages : unreadMessages.unread;
  expect(unreadValue).toBeGreaterThanOrEqual(0);

  /* ---------- 日程：新建→月历→取消 ---------- */
  const scheduleTitle = `E2E日程-${suffix}`;
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const schedule = await apiPost<{ id: number }>('/schedules', staff.token, {
    scheduleTitle,
    scheduleType: 3,
    startTime: isoTime(1),
    endTime: isoTime(2),
    location: 'E2E 线上',
    description: `E2E 日程 ${suffix}`,
  });
  const calendar = await apiGet<{ items?: Array<{ title?: string; scheduleTitle?: string }> }>(
    `/schedules/calendar?month=${year}-${month}`,
    staff.token,
  );
  const calendarTitles = (calendar.items ?? []).map((item) => item.title ?? item.scheduleTitle ?? '');
  expect(calendarTitles.some((title) => title.includes(suffix))).toBeTruthy();
  await apiPost(`/schedules/${schedule.id}/cancel`, staff.token);
  const calendarAfter = await apiGet<{ items?: Array<{ id?: number; status?: number }> }>(
    `/schedules/calendar?month=${year}-${month}`,
    staff.token,
  );
  const cancelled = (calendarAfter.items ?? []).find((item) => Number(item.id) === Number(schedule.id));
  expect(cancelled?.status).toBe(3);

  /* ---------- 清理（能删则删；流程实例/借阅/消息不可删，记入残留清单） ---------- */
  for (const [name, path, token] of [
    ['task', `/tasks/${task.id}`, staff.token],
    ['schedule', `/schedules/${schedule.id}`, staff.token],
    ['notice', `/notices/${notice.id}`, icpadmin.token],
    ['doc', `/docs/${doc.id}`, icpadmin.token],
  ] as const) {
    try {
      await apiDelete(path, token);
    } catch {
      // 已发布/归档文档、已发布公告等不可删，保留为示例数据之外的残留，报告中列出
      console.log(`[E13] 残留 ${name} id=${path.split('/').pop()}`);
    }
  }

  expectNoIssues(issues, 'E13 协同运营');
});
