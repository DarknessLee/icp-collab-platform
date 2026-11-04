import { test, expect } from '@playwright/test';
import {
  apiGet,
  apiLogin,
  expectNoIssues,
  percentText,
  uiLogin,
  watchPage,
  type ApiBody,
} from './helpers';

interface Summary {
  policies: { total: number; effective: number };
  risks: { total: number; high: number };
  coverage: { totalRisks: number; coveredRisks: number; coverageRate: number };
  defects: { total: number; unclosed: number };
  rectify: { total: number; approved: number; rate: number };
  workflow: { todo: number; overdue: number };
  tasks: { overdue: number };
  messages: { unread: number };
}

interface Trend {
  labels: string[];
  newPolicies: number[];
  newDefects: number[];
  rectifyClosed: number[];
  avgProcessHours: number[];
}

test.describe('E02 工作台', () => {
  test('E02 admin 8 统计卡 + 趋势图；数字与 /dashboard/summary 同源一致', async ({ page }) => {
    const issues = watchPage(page);
    const auth = await apiLogin('admin');
    const summary = await apiGet<Summary>('/dashboard/summary', auth.token);
    const trend = await apiGet<Trend>('/dashboard/trend?months=6', auth.token);
    expect(trend.labels).toHaveLength(6);

    await uiLogin(page, 'admin');
    await expect(page.locator('.dash-card')).toHaveCount(8);

    const card = (title: string) =>
      page.locator('.dash-card').filter({ hasText: title }).first();

    const expectCard = async (title: string, value: string, hint?: string) => {
      const target = card(title);
      await expect(target.locator('.dash-card__title')).toHaveText(title);
      await expect(target.locator('.dash-card__value')).toHaveText(value);
      if (hint) {
        const hintText = await target.locator('.dash-card__hint').innerText();
        expect(hintText, `${title} 提示与接口一致`).toContain(hint);
      }
    };

    await expectCard('制度总数', String(summary.policies.total), `有效制度 ${summary.policies.effective}`);
    await expectCard('风险总数', String(summary.risks.total), `高风险 ${summary.risks.high}`);
    await expectCard(
      '控制矩阵覆盖率',
      percentText(summary.coverage.coverageRate),
      `${summary.coverage.coveredRisks} / ${summary.coverage.totalRisks} 项风险已覆盖`,
    );
    await expectCard('缺陷总数', String(summary.defects.total), `未关闭 ${summary.defects.unclosed}`);
    await expectCard(
      '整改完成率',
      percentText(summary.rectify.rate),
      `已通过 ${summary.rectify.approved} / ${summary.rectify.total}`,
    );
    await expectCard('待办审批', String(summary.workflow.todo), `超时流程 ${summary.workflow.overdue}`);
    await expectCard('超期任务', String(summary.tasks.overdue));
    await expectCard('未读消息', String(summary.messages.unread));

    await expect(page.locator('.dash-chart canvas')).toHaveCount(1);
    const bodyText = await page.locator('.page-container').first().innerText();
    expect(bodyText).not.toContain('NaN');
    expect(bodyText).not.toContain('undefined');

    expectNoIssues(issues, 'E02');
  });

  test('E02-admin API 与页面卡片逐卡一致（趋势接口结构）', async () => {
    const auth = await apiLogin('admin');
    const body = await apiGet<Summary>('/dashboard/summary', auth.token);
    // 趋势字段长度一致、无 null
    const trend = await apiGet<Trend>('/dashboard/trend?months=6', auth.token);
    for (const key of ['newPolicies', 'newDefects', 'rectifyClosed', 'avgProcessHours'] as const) {
      expect(trend[key], `trend.${key} 长度`).toHaveLength(trend.labels.length);
      expect(trend[key].every((v) => typeof v === 'number' && Number.isFinite(v))).toBeTruthy();
    }
    expect(body.coverage.coveredRisks <= body.coverage.totalRisks).toBeTruthy();
  });
});
