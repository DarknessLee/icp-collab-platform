import { expect, test } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  apiPost,
  apiPostRaw,
  approveChain,
  findInstance,
  uiLogin,
  uniqueSuffix,
  watchPage,
  expectNoIssues,
} from './helpers';

interface EvaluationRow {
  id: number;
  planCode: string;
  planName: string;
  status: number;
  taskCount?: number;
}

interface TaskRow {
  id: number;
  taskCode: string;
  planId: number;
  status: number;
}

const today = new Date();
const iso = (offsetDays: number) =>
  new Date(today.getTime() + offsetDays * 86_400_000).toISOString().slice(0, 10);

test('E08 评价计划闭环：新建→提交审批→启动（任务生成）→底稿提交→完成→关闭', async ({ page }) => {
  test.setTimeout(300_000);
  const suffix = uniqueSuffix();
  const planName = `E2E评价计划-${suffix}`;

  const icpadmin = await apiLogin('icpadmin');
  const exec = await apiLogin('exec');
  const staff = await apiLogin('staff01');

  const plan = await apiPost<EvaluationRow>('/evaluations', icpadmin.token, {
    planName,
    planYear: today.getFullYear(),
    planType: 1,
    startDate: iso(0),
    endDate: iso(30),
    deptScope: '全公司',
    description: `E2E 评价计划 ${suffix}`,
  });
  expect(plan.id).toBeGreaterThan(0);
  expect(plan.planCode).toMatch(/^PJ-/);
  expect(plan.status).toBe(0);

  // 提交审批
  await apiPost(`/evaluations/${plan.id}/submit`, icpadmin.token);
  const instance = await findInstance(icpadmin.token, { title: planName });
  const finished = await approveChain(instance.processInstanceId, [
    { name: 'icpadmin', token: icpadmin.token },
    { name: 'exec', token: exec.token },
  ]);
  expect(finished).toBe('COMPLETED');

  // 启动评价：生成任务；重复 start 幂等（仍成功，任务数不翻倍）
  await apiPost(`/evaluations/${plan.id}/start`, icpadmin.token);
  const taskList = await apiGet<{ list?: TaskRow[]; total?: number }>(
    `/evaluation-tasks?pageNum=1&pageSize=500&planId=${plan.id}`,
    icpadmin.token,
  );
  const tasks = taskList.list ?? [];
  expect(tasks.length).toBeGreaterThan(0);
  const countAfterFirstStart = tasks.length;
  await apiPost(`/evaluations/${plan.id}/start`, icpadmin.token);
  const taskList2 = await apiGet<{ list?: TaskRow[] }>(
    `/evaluation-tasks?pageNum=1&pageSize=500&planId=${plan.id}`,
    icpadmin.token,
  );
  expect((taskList2.list ?? []).length).toBe(countAfterFirstStart);

  // 逐任务开始并提交底稿（评分 85 + 评价结论 1 + 底稿结论「有效」）
  for (const task of tasks) {
    if (task.status === 0) await apiPost(`/evaluation-tasks/${task.id}/start`, icpadmin.token);
    await apiPost(`/evaluation-tasks/${task.id}/submit`, icpadmin.token, {
      score: 85,
      conclusion: 1,
      workpaper: {
        sampleDesc: `E2E 抽样 ${suffix}`,
        testDesc: '按控制点执行穿行测试，检查审批留痕与授权',
        testResult: 1,
        evidence: [],
        conclusion: '有效',
      },
    });
  }
  const tasksDone = await apiGet<{ list?: TaskRow[] }>(
    `/evaluation-tasks?pageNum=1&pageSize=500&planId=${plan.id}`,
    icpadmin.token,
  );
  expect((tasksDone.list ?? []).every((row) => row.status === 2)).toBeTruthy();

  // 完成 → 关闭（状态机：完成后计划关闭）
  await apiPost(`/evaluations/${plan.id}/finish`, icpadmin.token);
  let detail = await apiGet<EvaluationRow>(`/evaluations/${plan.id}`, icpadmin.token);
  const statusAfterFinish = detail.status;
  expect(statusAfterFinish).toBeGreaterThanOrEqual(3);

  await apiPost(`/evaluations/${plan.id}/close`, icpadmin.token);
  detail = await apiGet<EvaluationRow>(`/evaluations/${plan.id}`, icpadmin.token);
  expect(detail.status).toBeGreaterThan(statusAfterFinish);

  // 状态机保护：已关闭计划重复提交/启动必须被拒绝（业务错误码 40001，HTTP 200 契约）
  const illegal = await apiPostRaw(`/evaluations/${plan.id}/submit`, icpadmin.token);
  expect(illegal.code).not.toBe(0);

  // UI 复核计划列表状态标签与 API 同源
  await uiLogin(page, 'icpadmin');
  const issues = watchPage(page);
  await page.goto('/icp/evaluation');
  await page.locator('input[placeholder="计划名称 / 编码"]').fill(planName);
  await page.locator('input[placeholder="计划名称 / 编码"]').press('Enter');
  const row = page.locator('.el-table__row').filter({ hasText: planName }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await expect(row).toContainText('已关闭');

  expectNoIssues(issues, 'E08 评价计划');
});
