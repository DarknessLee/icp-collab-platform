import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  approveTask,
  findTodo,
  instanceDetail,
  instanceStatus,
  rejectTask,
  uniqueSuffix,
  urgeTask,
  waitInstanceStatus,
} from './helpers';

const today = new Date();
const iso = (offsetDays: number) =>
  new Date(today.getTime() + offsetDays * 86_400_000).toISOString().slice(0, 10);

test('E11 驳回→重新提交→历史留痕；连续催办 2 次不改状态', async () => {
  test.setTimeout(180_000);
  const suffix = uniqueSuffix();
  const title = `E2E用印驳回-${suffix}`;

  const staff = await apiLogin('staff01');
  const dept = await apiLogin('dept01');

  const created = await apiPost<{ workflow?: { processInstanceId?: string; taskId?: string } }>(
    '/workflow/seal',
    staff.token,
    {
      sealType: 1,
      reason: `E2E 用印驳回重提 ${suffix}`,
      useDate: iso(1),
      submit: true,
    },
  );
  expect(created.workflow?.processInstanceId).toBeTruthy();
  const instanceId = String(created.workflow!.processInstanceId);

  // 发起节点由申请人先确认，再流转到部门负责人
  const startTodo = await findTodo(staff.token, { processInstanceId: instanceId });
  await approveTask(staff.token, startTodo.taskId, 'E2E 发起确认');

  const deptTodo = await findTodo(dept.token, { processInstanceId: instanceId });
  const rejectComment = `E2E 驳回意见 ${suffix}`;
  await rejectTask(dept.token, deptTodo.taskId, rejectComment);
  const rejected = await waitInstanceStatus(staff.token, instanceId, 'REJECTED');
  expect(rejected).toBe('REJECTED');

  const historyAfterReject = await apiGet<Array<{ action?: string; comment?: string }>>(
    `/workflow/instances/${instanceId}/history`,
    staff.token,
  );
  expect(historyAfterReject.some((item) => /reject/i.test(String(item.action ?? '')))).toBeTruthy();
  expect(historyAfterReject.some((item) => String(item.comment ?? '').includes(rejectComment))).toBeTruthy();

  // 重新提交 → 实例回到运行中
  await apiPost(`/workflow/instances/${instanceId}/resubmit`, staff.token);
  expect(await waitInstanceStatus(staff.token, instanceId, 'RUNNING')).toBe('RUNNING');
  const historyAfterResubmit = await apiGet<Array<{ action?: string }>>(
    `/workflow/instances/${instanceId}/history`,
    staff.token,
  );
  expect(historyAfterResubmit.length).toBeGreaterThan(historyAfterReject.length);

  // 催办 2 次：只写留痕/站内信，不改变流程状态
  const todoAfterResubmit = await findTodo(dept.token, { processInstanceId: instanceId });
  await urgeTask(dept.token, todoAfterResubmit.taskId, `E2E 催办 1 ${suffix}`);
  const detail1 = await instanceDetail(staff.token, instanceId);
  expect(instanceStatus(detail1.status)).toBe('RUNNING');
  await new Promise((resolve) => setTimeout(resolve, 500));
  await urgeTask(dept.token, todoAfterResubmit.taskId, `E2E 催办 2 ${suffix}`);
  const detail2 = await instanceDetail(staff.token, instanceId);
  expect(instanceStatus(detail2.status)).toBe('RUNNING');
});
