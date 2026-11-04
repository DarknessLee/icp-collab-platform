import { expect, test } from '@playwright/test';
import path from 'node:path';
import {
  apiGet,
  apiLogin,
  apiPost,
  apiUpload,
  expectNoIssues,
  uiLogin,
  uniqueSuffix,
  watchPage,
} from './helpers';

interface DefectRow {
  id: number;
  defectCode: string;
  defectName: string;
  status: number;
  rectifyId?: number;
}

interface RectifyRow {
  id: number;
  rectifyCode: string;
  defectId: number;
  status: number;
  evidence?: string;
}

const today = new Date();
const iso = (offsetDays: number) =>
  new Date(today.getTime() + offsetDays * 86_400_000).toISOString().slice(0, 10);

const EVIDENCE_FILE =
  process.env.E2E_EVIDENCE_FILE || path.resolve(process.cwd(), 'fixtures', 'evidence.pdf');

test('E09 缺陷→整改→证据→复核通过→缺陷关闭（状态机与证据留痕）', async ({ page }) => {
  test.setTimeout(180_000);
  const suffix = uniqueSuffix();
  const defectName = `E2E缺陷整改-${suffix}`;

  const icpadmin = await apiLogin('icpadmin');
  const deptId = Number((icpadmin.raw as { user?: { deptId?: number } }).user?.deptId ?? 0);

  const defect = await apiPost<DefectRow>('/defects', icpadmin.token, {
    defectName,
    sourceType: 1,
    severity: 1,
    defectType: 1,
    deptId: deptId || undefined,
    findDate: iso(0),
    description: `E2E 缺陷描述 ${suffix}`,
  });
  expect(defect.id).toBeGreaterThan(0);
  expect(defect.defectCode).toMatch(/^QX-/);
  expect(defect.status).toBe(0);

  // 发起整改
  await apiPost(`/defects/${defect.id}/rectify`, icpadmin.token, {
    rectifyPlan: `E2E 整改计划 ${suffix}`,
    responsibleUserId: icpadmin.userId,
    deadline: iso(7),
  });

  const rectifyPage = await apiGet<{ list?: RectifyRow[] }>(
    `/rectifies?pageNum=1&pageSize=200&keyword=${encodeURIComponent(suffix)}`,
    icpadmin.token,
  );
  let rectify = (rectifyPage.list ?? []).find((row) => Number(row.defectId) === defect.id);
  if (!rectify) {
    const all = await apiGet<{ list?: RectifyRow[] }>('/rectifies?pageNum=1&pageSize=500', icpadmin.token);
    rectify = (all.list ?? []).find((row) => Number(row.defectId) === defect.id);
  }
  expect(rectify).toBeTruthy();
  expect(rectify!.status).toBe(0);

  // 上传证据并提交整改（后端 RectifySubmit.evidence 为逗号分隔 String）
  const file = await apiUpload(EVIDENCE_FILE, icpadmin.token);
  expect(file.id).toBeGreaterThan(0);
  await apiPost(`/rectifies/${rectify!.id}/submit`, icpadmin.token, {
    evidence: String(file.id),
    remark: `E2E 整改说明 ${suffix}`,
    finishDate: iso(0),
  });
  const submitted = await apiGet<RectifyRow>(`/rectifies/${rectify!.id}`, icpadmin.token);
  expect(submitted.status).toBe(1);
  expect(submitted.evidence ?? '').toContain(String(file.id));

  // 复核通过
  await apiPost(`/rectifies/${rectify!.id}/approve`, icpadmin.token);
  const approved = await apiGet<RectifyRow>(`/rectifies/${rectify!.id}`, icpadmin.token);
  expect(approved.status).toBe(2);

  // 业务顺序：整改通过后才能关闭缺陷；关闭缺陷后再关闭整改单
  await apiPost(`/defects/${defect.id}/close`, icpadmin.token);
  const closedDefect = await apiGet<DefectRow>(`/defects/${defect.id}`, icpadmin.token);
  expect(closedDefect.status).toBe(3);

  await apiPost(`/rectifies/${rectify!.id}/close`, icpadmin.token);
  const closedRectify = await apiGet<RectifyRow>(`/rectifies/${rectify!.id}`, icpadmin.token);
  expect(closedRectify.status).toBeGreaterThanOrEqual(2);

  // UI 复核缺陷台账已关闭
  await uiLogin(page, 'icpadmin');
  const issues = watchPage(page);
  await page.goto('/icp/defect');
  await page.locator('input[placeholder="缺陷名称 / 编码"]').fill(defectName);
  await page.locator('input[placeholder="缺陷名称 / 编码"]').press('Enter');
  const row = page.locator('.el-table__row').filter({ hasText: defectName }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await expect(row).toContainText('已关闭');

  expectNoIssues(issues, 'E09 缺陷整改');
});
