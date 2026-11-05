import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  approveTask,
  findTodo,
  waitInstanceStatus,
  uniqueSuffix,
  type TodoRow,
} from './helpers';

interface PurchaseCreated {
  id?: number;
  applyNo?: string;
  workflow?: { processInstanceId?: string; taskId?: string };
}

const today = new Date().toISOString().slice(0, 10);

async function startPurchase(token: string, title: string, amount: number): Promise<PurchaseCreated> {
  return apiPost<PurchaseCreated>('/workflow/purchase', token, {
    title,
    amount,
    budgetAmount: 20000,
    purchaseType: 1,
    supplier: 'E2E 供应商',
    purpose: `E17 金额分级审批 ${amount}`,
    applyDate: today,
    submit: true,
  });
}

async function todosOf(token: string, instanceId: string): Promise<TodoRow[]> {
  const data = await apiGet<{ list?: TodoRow[] }>('/workflow/todo?pageNum=1&pageSize=200', token);
  return (data.list ?? []).filter((row) => String(row.processInstanceId) === String(instanceId));
}

test.describe('E17 采购金额分级审批', () => {
  test('E17 大额（>5000）：部门审批后追加管理层审批', async () => {
    test.setTimeout(120_000);
    const staff = await apiLogin('staff01');
    const dept = await apiLogin('dept01');
    const exec = await apiLogin('exec');
    const title = `E2E大额采购-${uniqueSuffix()}`;

    const created = await startPurchase(staff.token, title, 12345.67);
    const instanceId = created.workflow?.processInstanceId as string;
    expect(instanceId).toBeTruthy();

    await approveTask(staff.token, created.workflow?.taskId as string, '提交大额采购');

    const deptTodo = await findTodo(dept.token, { processInstanceId: instanceId });
    expect(deptTodo.nodeName).toBe('部门审批');
    await approveTask(dept.token, deptTodo.taskId, '部门同意');

    const execTodo = await findTodo(exec.token, { processInstanceId: instanceId });
    expect(execTodo.nodeName).toBe('管理层审批');
    await approveTask(exec.token, execTodo.taskId, '管理层同意');

    expect(await waitInstanceStatus(staff.token, instanceId, 'COMPLETED')).toBe('COMPLETED');
  });

  test('E17 小额（≤5000）：部门审批后直接完成，无管理层节点', async () => {
    test.setTimeout(120_000);
    const staff = await apiLogin('staff01');
    const dept = await apiLogin('dept01');
    const exec = await apiLogin('exec');
    const title = `E2E小额采购-${uniqueSuffix()}`;

    const created = await startPurchase(staff.token, title, 4000);
    const instanceId = created.workflow?.processInstanceId as string;
    expect(instanceId).toBeTruthy();

    await approveTask(staff.token, created.workflow?.taskId as string, '提交小额采购');

    const deptTodo = await findTodo(dept.token, { processInstanceId: instanceId });
    expect(deptTodo.nodeName).toBe('部门审批');
    await approveTask(dept.token, deptTodo.taskId, '部门同意');

    expect(await waitInstanceStatus(staff.token, instanceId, 'COMPLETED')).toBe('COMPLETED');
    const execTodos = await todosOf(exec.token, instanceId);
    expect(execTodos).toHaveLength(0);
  });
});
