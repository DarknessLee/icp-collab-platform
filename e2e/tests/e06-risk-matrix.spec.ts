import { expect, test } from '@playwright/test';
import {
  apiDelete,
  apiGet,
  apiLogin,
  apiPost,
  apiPostRaw,
  expectNoIssues,
  paginationTotal,
  uiLogin,
  uniqueSuffix,
  watchPage,
} from './helpers';

interface CategoryNode {
  id: number;
  categoryName: string;
  children?: CategoryNode[];
}

interface RiskRow {
  id: number;
  riskCode: string;
  riskName: string;
  riskLevel: number;
  riskScore: number;
  probability?: number;
  impact?: number;
  status?: number;
}

interface ControlRow {
  id: number;
  controlCode: string;
  controlName: string;
}

interface MatrixRow {
  id: number;
  matrixCode: string;
  riskId: number;
  controlId: number;
}

interface CoverageRaw {
  totalRisks: number;
  coveredRisks: number;
  uncoveredRisks: number;
  coverageRate: number;
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

test('E06 风险自动定级 + 矩阵唯一冲突拦截 + 覆盖率与 API 同源', async ({ page }) => {
  test.setTimeout(150_000);
  const suffix = uniqueSuffix();
  const highRiskName = `E2E高风险-${suffix}`;
  const midRiskName = `E2E中风险-${suffix}`;

  const icpadmin = await apiLogin('icpadmin');
  const categories = await apiGet<CategoryNode[]>('/risk-categories/tree', icpadmin.token);
  const categoryId = leafCategoryId(categories);
  expect(categoryId).toBeGreaterThan(0);

  // 概率 × 影响自动定级：5×3=15 → 高（1）；2×4=8 → 中（2）
  const highRisk = await apiPost<RiskRow>('/risks', icpadmin.token, {
    riskName: highRiskName,
    categoryId,
    probability: 5,
    impact: 3,
    description: 'E2E 概率×影响自动定级（高）',
  });
  expect(highRisk.riskScore).toBe(15);
  expect(highRisk.riskLevel).toBe(1);

  const midRisk = await apiPost<RiskRow>('/risks', icpadmin.token, {
    riskName: midRiskName,
    categoryId,
    probability: 2,
    impact: 4,
    description: 'E2E 概率×影响自动定级（中）',
  });
  expect(midRisk.riskScore).toBe(8);
  expect(midRisk.riskLevel).toBe(2);

  // 矩阵唯一约束：先取一个控制点，建立风险-控制点关联；重复创建必须被拦截（40000/40002）
  const controlPage = await apiGet<{ list?: ControlRow[] }>('/controls?pageNum=1&pageSize=10', icpadmin.token);
  const control = (controlPage.list ?? [])[0];
  expect(control?.id).toBeTruthy();
  const matrix = await apiPost<MatrixRow>('/matrices', icpadmin.token, {
    riskId: highRisk.id,
    controlId: control.id,
    coverage: 1,
    evidenceDesc: 'E2E 矩阵覆盖证据',
  });
  expect(matrix.matrixCode).toMatch(/^JC-/);
  const duplicate = await apiPostRaw('/matrices', icpadmin.token, {
    riskId: highRisk.id,
    controlId: control.id,
    coverage: 1,
  });
  expect([40000, 40002]).toContain(duplicate.code);

  // 覆盖率：页面卡片数字必须与 /matrices/coverage 同源（不是写死的 100%）
  const coverage = await apiGet<CoverageRaw>('/matrices/coverage', icpadmin.token);
  expect(coverage.totalRisks).toBeGreaterThan(0);
  expect(coverage.coveredRisks + coverage.uncoveredRisks).toBe(coverage.totalRisks);
  const expectedRate = `${((coverage.coveredRisks / coverage.totalRisks) * 100).toFixed(1)}%`;

  await uiLogin(page, 'icpadmin');
  const issues = watchPage(page);
  await page.goto('/icp/risk');
  await page.locator('input[placeholder="风险名称 / 编码"]').fill(highRiskName);
  await page.locator('input[placeholder="风险名称 / 编码"]').press('Enter');
  const riskRow = page.locator('.el-table__row').filter({ hasText: highRiskName }).first();
  await expect(riskRow).toBeVisible({ timeout: 20_000 });
  await expect(riskRow).toContainText('高');
  expect(await paginationTotal(page)).toBeGreaterThanOrEqual(1);

  await page.goto('/icp/matrix');
  await expect(page.locator('.coverage-card').first()).toContainText(`${coverage.totalRisks}`);
  await expect(page.locator('.coverage-card').nth(1)).toContainText(`${coverage.coveredRisks}`);
  await expect(page.locator('.coverage-card').nth(2)).toContainText(`${coverage.uncoveredRisks}`);
  await expect(page.locator('.coverage-card').nth(3)).toContainText(expectedRate);

  // 清理：删除矩阵关联与两条风险（风险删除为逻辑删除）
  await apiDelete(`/matrices/${matrix.id}`, icpadmin.token);
  await apiDelete(`/risks/${highRisk.id}`, icpadmin.token);
  await apiDelete(`/risks/${midRisk.id}`, icpadmin.token);

  expectNoIssues(issues, 'E06 风险/矩阵');
});
