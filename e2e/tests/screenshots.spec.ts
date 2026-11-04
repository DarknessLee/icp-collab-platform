import { test, expect, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { apiGet, apiLogin, uiLogin, type MenuNode } from './helpers';

/**
 * M3 最终截图（S01–S28）。
 * 仅在演示态（scripts/demo-reset.ps1 → 60 实例/40 待办/20 催办）运行：
 *   npm run e2e:shots
 * 其余场景（如 clean seed）自动 skip，保证 `npm run e2e` 不受影响。
 */
const SHOTS_DIR = path.resolve(process.cwd(), '..', 'docs', 'screenshots');

interface DashboardSummary {
  policies: { total: number; effective: number };
  risks: { total: number; high: number };
  coverage: { totalRisks: number; coveredRisks: number; coverageRate: number };
  defects: { total: number; unclosed: number };
  rectify: { total: number; approved: number; rate: number };
  workflow: { todo: number; overdue: number };
  tasks: { overdue: number };
  messages: { unread: number };
}

interface ScreenshotCase {
  id: string;
  file: string;
  desc: string;
  path: string;
  role: string;
  /** 可选：进入页面后的额外操作（截图前执行） */
  prepare?: (page: Page) => Promise<void>;
}

test.describe('S01–S28 最终截图', () => {
  test.skip(process.env.E2E_DEMO !== '1', '最终截图仅在演示态运行（npm run e2e:shots）');

  test('生成演示态最终截图与 INDEX.md', async ({ page }) => {
    test.setTimeout(15 * 60_000);
    await mkdir(SHOTS_DIR, { recursive: true });

    const auth = await apiLogin('admin');
    const summary = await apiGet<DashboardSummary>('/dashboard/summary', auth.token);
    const menus: MenuNode[] = auth.menus;
    expect(menus.length).toBeGreaterThan(0);

    const cases: ScreenshotCase[] = [
      { id: 'S01', file: 's01-login.png', desc: '登录页', path: '/login', role: '匿名' },
      { id: 'S02', file: 's02-dashboard.png', desc: 'admin 工作台', path: '/dashboard', role: 'admin' },
      { id: 'S03', file: 's03-report-overview.png', desc: '内控概览报表', path: '/report/overview', role: 'admin' },
      { id: 'S04', file: 's04-policy-list.png', desc: '制度列表', path: '/icp/policy', role: 'admin' },
      { id: 'S05', file: 's05-risk-list.png', desc: '风险库+评分', path: '/icp/risk', role: 'icpadmin' },
      { id: 'S06', file: 's06-matrix.png', desc: '控制矩阵+覆盖率', path: '/icp/matrix', role: 'admin' },
      { id: 'S07', file: 's07-evaluation.png', desc: '评价计划与任务', path: '/icp/evaluation', role: 'icpadmin' },
      { id: 'S08', file: 's08-defect.png', desc: '缺陷台账', path: '/icp/defect', role: 'icpadmin' },
      { id: 'S09', file: 's09-rectify.png', desc: '整改复核', path: '/icp/rectify', role: 'icpadmin' },
      { id: 'S10', file: 's10-todo.png', desc: '待办中心', path: '/wf/todo', role: 'icpadmin' },
      { id: 'S11', file: 's11-monitor.png', desc: '流程监控', path: '/wf/monitor', role: 'icpadmin' },
      { id: 'S12', file: 's12-purchase.png', desc: '采购申请', path: '/wf/purchase', role: 'staff01' },
      { id: 'S13', file: 's13-seal.png', desc: '用印申请', path: '/wf/seal', role: 'staff01' },
      { id: 'S14', file: 's14-borrow.png', desc: '文档借阅', path: '/coll/doc-borrow', role: 'staff01' },
      { id: 'S15', file: 's15-collab-task.png', desc: '任务中心', path: '/coll/task', role: 'staff01' },
      { id: 'S16', file: 's16-doc-center.png', desc: '文档中心', path: '/coll/doc', role: 'icpadmin' },
      { id: 'S17', file: 's17-notice.png', desc: '公告通知', path: '/coll/notice', role: 'staff01' },
      { id: 'S18', file: 's18-message.png', desc: '消息中心（SSE）', path: '/coll/message', role: 'staff01' },
      { id: 'S19', file: 's19-schedule.png', desc: '日程月历', path: '/coll/schedule', role: 'staff01' },
      { id: 'S20', file: 's20-report-defect.png', desc: '报表-缺陷', path: '/report/defect', role: 'admin' },
      { id: 'S21', file: 's21-alert.png', desc: '预警中心', path: '/report/alert', role: 'admin' },
      { id: 'S22', file: 's22-audit-log.png', desc: '审计操作日志', path: '/system/log', role: 'admin' },
      { id: 'S23', file: 's23-role-menu.png', desc: '角色菜单授权', path: '/system/role', role: 'admin' },
      { id: 'S24', file: 's24-profile.png', desc: '个人中心', path: '/profile', role: 'admin' },
      { id: 'S25', file: 's25-forbidden-403.png', desc: '6 角色 403 对比', path: '/report/alert', role: 'staff01' },
      {
        id: 'S26',
        file: 's26-policy-detail.png',
        desc: '制度详情 + 审批历史',
        path: '/icp/policy',
        role: 'icpadmin',
        prepare: async (target) => {
          await target.locator('.el-table__row').first().waitFor({ state: 'visible', timeout: 20_000 });
          await target.locator('.el-table__row').first().locator('button:has-text("详情")').click();
          await target.locator('.el-drawer:visible').first().waitFor({ state: 'visible', timeout: 20_000 });
        },
      },
      {
        id: 'S27',
        file: 's27-todo-detail.png',
        desc: '待办详情（表单 + 历史 + BPMN）',
        path: '/wf/todo',
        role: 'icpadmin',
        prepare: async (target) => {
          await target.locator('.el-table__row').first().waitFor({ state: 'visible', timeout: 20_000 });
          await target.locator('.el-table__row').first().locator('button:has-text("处理")').click();
          await target.locator('.el-drawer:visible').first().waitFor({ state: 'visible', timeout: 20_000 });
        },
      },
      {
        id: 'S28',
        file: 's28-mobile-sidebar.png',
        desc: '窄屏侧栏抽屉',
        path: '/dashboard',
        role: 'admin',
        prepare: async (target) => {
          await target.setViewportSize({ width: 390, height: 844 });
          await target.locator('.navbar-icon-button').click();
          await target.locator('.sidebar.mobile-open').waitFor({ state: 'visible', timeout: 10_000 });
        },
      },
    ];

    const rows: string[] = [];
    for (const item of cases) {
      if (item.role === '匿名') {
        await page.goto(item.path);
      } else {
        await page.context().clearCookies();
        await page.goto('/login');
        await page.evaluate(() => {
          localStorage.clear();
          sessionStorage.clear();
        });
        await uiLogin(page, item.role);
        await page.goto(item.path);
      }
      if (item.prepare) await item.prepare(page);
      // 统一复位滚动位置：点击链接/展开抽屉后容器可能被横向滚动，导致左侧列被裁切
      await page.evaluate(() => {
        window.scrollTo(0, 0);
        document
          .querySelectorAll<HTMLElement>(
            '.el-table__body-wrapper, .el-table__header-wrapper, .el-scrollbar__wrap, .el-scrollbar__view',
          )
          .forEach((el) => {
            el.scrollLeft = 0;
            el.scrollTop = 0;
          });
      });
      await page.waitForTimeout(800);
      await page.screenshot({ path: path.join(SHOTS_DIR, item.file), fullPage: false });
      rows.push(
        `| ${item.id} | \`${item.file}\` | ${item.desc} | \`${item.path}\` | ${item.role} | ${summary.policies.total} / ${summary.risks.total} / ${summary.defects.total} |`,
      );
    }

    const index = [
      '# M3 最终截图索引（S01–S28）',
      '',
      `生成时间：${new Date().toISOString()}`,
      '',
      '> 截图取自演示态（`scripts/demo-reset.ps1`）：**60 实例 / 40 待办 / 20 催办**，种子基准日 **2026-10-31**；前端为 4173 预览构建。',
      '> 制度 52 条（其中 seed-flow 追加 12 条流程政策）、风险 40 条、缺陷 30 条。',
      '',
      '| 编号 | 文件 | 说明 | 页面路径 | 角色 | 种子数字（制度/风险/缺陷） |',
      '| --- | --- | --- | --- | --- | --- |',
      ...rows,
      '',
    ].join('\n');
    await writeFile(path.join(SHOTS_DIR, 'INDEX.md'), index, 'utf8');

    expect(rows.length).toBe(28);
  });
});
