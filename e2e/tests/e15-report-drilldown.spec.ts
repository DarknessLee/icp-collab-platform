import { expect, test, type Page } from '@playwright/test';
import { uiLogin } from './helpers';

/**
 * E15 报表图表下钻（M6-W1-2）：
 * - 缺陷统计柱状图点击 → 缺陷管理并带「严重程度 + 状态」筛选
 * - 制度与风险统计饼图点击 → 风险库并带「风险等级」筛选
 * - 任务与流程统计的流程类型参数可直达流程监控
 */
test.describe('E15 报表图表下钻', () => {
  async function chartBox(page: Page, selector: string): Promise<{ x: number; y: number; width: number; height: number }> {
    const canvas = page.locator(`${selector} canvas`).first();
    await expect(canvas).toBeVisible({ timeout: 20_000 });
    const box = await canvas.boundingBox();
    if (!box) throw new Error(`未取到图表画布坐标：${selector}`);
    return box;
  }

  test('缺陷图表点击下钻缺陷列表并带筛选参数', async ({ page }) => {
    await uiLogin(page, 'admin');
    const dataLoaded = page.waitForResponse((res) => res.url().includes('/reports/defects'), {
      timeout: 20_000
    });
    await page.goto('/report/defect');
    await dataLoaded;
    await page.waitForTimeout(800);
    const box = await chartBox(page, '.chart');
    // 第一组（重大）柱子区域：横向约 26%、纵向约 40%
    await page.mouse.click(box.x + box.width * 0.26, box.y + box.height * 0.4);
    await page.waitForURL(/\/icp\/defect\?severity=\d+&status=\d+/, { timeout: 15_000 });
    const url = new URL(page.url());
    expect(url.searchParams.get('severity')).toMatch(/^\d$/);
    expect(url.searchParams.get('status')).toMatch(/^\d$/);

    // 列表页把 URL 参数转成查询条件：首个列表请求应带 severity/status
    const request = await page.waitForRequest(
      (req) => req.url().includes('/v1/defects?') && req.url().includes('severity='),
      { timeout: 15_000 }
    );
    expect(request.url()).toContain(`severity=${url.searchParams.get('severity')}`);
    expect(request.url()).toContain(`status=${url.searchParams.get('status')}`);
    await expect(page.locator('.toolbar-select').first()).toBeVisible();
  });

  test('风险分布饼图点击下钻风险库并带风险等级', async ({ page }) => {
    await uiLogin(page, 'admin');
    const dataLoaded = page.waitForResponse((res) => res.url().includes('/reports/risk-distribution'), {
      timeout: 20_000
    });
    await page.goto('/report/policy');
    await dataLoaded;
    await page.waitForTimeout(800);
    const box = await chartBox(page, '.risk-chart');
    await page.mouse.click(box.x + box.width * 0.58, box.y + box.height * 0.3);
    await page.waitForURL(/\/icp\/risk\?riskLevel=\d+/, { timeout: 15_000 });
    const level = new URL(page.url()).searchParams.get('riskLevel');
    expect(level).toMatch(/^\d$/);
    const request = await page.waitForRequest(
      (req) => req.url().includes('/v1/risks?') && req.url().includes('riskLevel='),
      { timeout: 15_000 }
    );
    expect(request.url()).toContain(`riskLevel=${level}`);
  });

  test('流程监控支持 processKey 直达筛选', async ({ page }) => {
    await uiLogin(page, 'admin');
    const requestPromise = page.waitForRequest(
      (req) => req.url().includes('/workflow/monitor') && req.url().includes('processKey='),
      { timeout: 20_000 }
    );
    await page.goto('/wf/monitor?processKey=policy_approval');
    const request = await requestPromise;
    expect(request.url()).toContain('processKey=policy_approval');
    await expect(page.locator('.toolbar-select').first()).toBeVisible();
  });
});
