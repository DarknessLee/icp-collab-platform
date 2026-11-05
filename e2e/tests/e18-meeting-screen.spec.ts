import { expect, test } from '@playwright/test';
import { apiDelete, apiGet, apiLogin, apiPost, apiPut, expectNoIssues, uiLogin, uniqueSuffix, watchPage } from './helpers';

interface MeetingRow {
  id: number;
  meetingNo?: string;
  title?: string;
  meetingType?: string;
  status?: number | string;
  minutes?: string;
  attendeeCount?: number;
}

interface PageData {
  list?: MeetingRow[];
  total?: number;
}

const pad = (n: number) => String(n).padStart(2, '0');
/** yyyy-MM-dd HH:mm:ss（后端全局时间格式，非 ISO T） */
const hourLater = (hours: number) => {
  const d = new Date(Date.now() + hours * 3600_000);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:00:00`;
};

test('E18 会议纪要：新建 → 列表检索 → 详情 → 写纪要置「已召开」→ 删除', async ({ page }) => {
  test.setTimeout(120_000);
  const suffix = uniqueSuffix();
  const title = `E2E会议纪要-${suffix}`;
  const admin = await apiLogin('admin');

  // API 建会（时间格式 yyyy-MM-dd HH:mm:ss）
  const created = await apiPost<MeetingRow>('/meetings', admin.token, {
    title,
    meetingType: '1',
    startTime: hourLater(24),
    endTime: hourLater(26),
    location: '总部 3 层会议室',
    attendeeNames: '李静,高敏,赵敏',
    remark: 'E2E 自动化验证',
  });
  expect(created.id, '新建会议应返回 id').toBeTruthy();
  expect(String(created.meetingNo ?? '')).toMatch(/^HY-\d{4}-\d{3}$/);
  expect(Number(created.status)).toBe(0);

  // API：列表按关键字检索命中
  const searched = await apiGet<PageData>(`/meetings?pageNum=1&pageSize=20&keyword=${encodeURIComponent(title)}`, admin.token);
  const hit = (searched.list ?? []).find((row) => (row.title ?? '').includes(title));
  expect(hit, '列表应按关键字检索到新建会议').toBeTruthy();

  // API：写纪要 → 状态置 1
  await apiPut(`/meetings/${created.id}/minutes`, admin.token, { minutes: '会议就演示范围与验收口径达成一致，按计划推进。' });
  const detail = await apiGet<MeetingRow>(`/meetings/${created.id}`, admin.token);
  expect(Number(detail.status)).toBe(1);
  expect(detail.minutes ?? '').toContain('达成一致');

  // UI：会议纪要页面检索 → 详情抽屉 → 纪要可见
  await uiLogin(page, 'admin');
  const issues = watchPage(page);
  await page.goto('/coll/meeting');
  await expect(page.locator('.el-table__row').first()).toBeVisible({ timeout: 20_000 });
  const search = page.locator('.toolbar-input input').first();
  await search.fill(title);
  await search.press('Enter');
  await page.waitForResponse(
    (res) => res.url().includes('/meetings?') && res.url().includes('keyword='),
    { timeout: 15_000 },
  );
  const row = page.locator('.el-table__row').filter({ hasText: title }).first();
  await expect(row).toBeVisible({ timeout: 15_000 });
  await row.locator('button:has-text("详情")').click();
  const drawer = page.locator('.el-drawer:visible').first();
  await expect(drawer).toBeVisible({ timeout: 15_000 });
  await expect(drawer.locator('.meeting-detail__title')).toContainText(title);
  await expect(drawer).toContainText('会议纪要');
  await expect(drawer).toContainText('达成一致');
  expectNoIssues(issues, 'E18 会议纪要');

  // 清理：删除该会议（不污染演示种子 20 条）
  await apiDelete(`/meetings/${created.id}`, admin.token);
  const after = await apiGet<PageData>(`/meetings?pageNum=1&pageSize=20&keyword=${encodeURIComponent(title)}`, admin.token);
  expect(after.total ?? 0, 'E2E 会议应已清理').toBe(0);
});

test('E19 数据大屏：/screen 渲染 6 KPI + 图表 + 预警，返回工作台可用', async ({ page }) => {
  test.setTimeout(120_000);
  await uiLogin(page, 'admin');
  const issues = watchPage(page);
  await page.goto('/screen');
  await expect(page.locator('.screen-panel').first()).toBeVisible({ timeout: 25_000 });
  await expect(page.locator('.kpi-card')).toHaveCount(6);
  await expect(page.locator('.screen-panel')).toHaveCount(6);
  await expect(page.locator('.screen-header__clock')).toBeVisible();
  await page.waitForTimeout(2500);
  const canvas = await page.locator('.screen-panel canvas').count();
  expect(canvas, '大屏至少 5 个图表画布').toBeGreaterThanOrEqual(5);

  // 返回工作台
  await page.locator('button:has-text("返回工作台"), a:has-text("返回工作台")').first().click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  expectNoIssues(issues, 'E19 数据大屏');
});
