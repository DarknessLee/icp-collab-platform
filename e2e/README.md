# M3 E2E 套件（Playwright）

仓库根 `e2e/` 是 M3 验收用的端到端套件：实现 docs/01 §3.2 的 E01–E14，并在演示态生成 docs/01 §3.3 的 S01–S28 最终截图。

## 环境要求

- Node.js 18+，本仓库使用 Node 24。
- 后端：`http://127.0.0.1:8080`（MySQL 3307）已启动、health UP，种子为演示态：
  - `scripts/demo-reset.ps1` 生成：60 流程实例（40 running / 20 completed）、icpadmin 待办 40、催办 20；
  - 制度 52（seed-flow 追加 12 条流程政策）、风险 40、缺陷 30、任务 80；菜单 179 / role_menu 540 / config 16；演示基准日 2026-10-31。
- 前端：`frontend/` 先 `npm run build`，再 `npm run preview -- --port 4173 --strictPort`（默认 baseURL 4173）。
- 可选覆盖：`E2E_BASE_URL`（如 http://127.0.0.1:5173）、`E2E_API_BASE`、`E2E_EVIDENCE_FILE`。
- 账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01` / `staff01`，统一密码 `Icp@2026`。

首次使用：

```powershell
cd e2e
npm install
# 本机若缺少 Playwright chromium（revision 1248，对应 @playwright/test 1.64.x）
npx playwright install chromium
```

## 常用命令

```powershell
cd e2e
npm run e2e          # 跑全部 24 条用例（15 个 spec 文件）
npm run e2e:list     # 列出用例
npm run e2e:shots    # E2E_DEMO=1，生成 S01–S28 到 ../docs/screenshots/ 并写 INDEX.md
```

也可单独挑选 spec，例如只读回归：

```powershell
npx playwright test tests/e01-login.spec.ts tests/e02-dashboard.spec.ts tests/e03-menus.spec.ts `
  tests/e04-datascope.spec.ts tests/e12-workflow.spec.ts tests/e14-audit.spec.ts --reporter=line
```

## 只读用例 vs 写库用例（重要）

| 类别 | Spec | 是否写库 | 说明 |
| --- | --- | --- | --- |
| 只读 | E01 登录、E02 工作台、E03 菜单、E04 数据权限、E12 流程中心、E14 审计 | 否 | 只登录、查询、校验页面数字与 API 同源，可反复跑 |
| 写库 | E05 制度、E06 风险/矩阵、E07 控制点、E08 评价、E09 缺陷/整改、E10 采购、E11 驳回重提、E13 协同 | 是 | 用唯一后缀创建数据并尽力清理；已发布文档/公告、已关闭计划/缺陷、流程实例/借阅等无法删除，会残留 |
| 截图 | `tests/screenshots.spec.ts`（`npm run e2e:shots`） | 否 | 仅在 `E2E_DEMO=1` 时运行，需纯净演示态 |

推荐顺序（避免截图被测试数据污染）：

1. 后端 `scripts/demo-reset.ps1` → 演示态 60/40/20；
2. 只读回归（上表第一行）确认数字；
3. 写库用例按 E05 → E06 → E07 → E08 → E09 → E10 → E11 → E13 顺序跑；
4. 再执行一次 `scripts/demo-reset.ps1` 回到纯净演示态；
5. `npm run e2e:shots` 生成最终截图与 `docs/screenshots/INDEX.md`。

截图要求：1440×900（S28 为 390×844 窄屏）、`sNN-<slug>.png`、≥25 张；INDEX.md 含文件名/说明/页面路径/角色/种子数字，并注明「演示态 60 实例/40 待办/20 催办、基准日 2026-10-31、制度 52/风险 40/缺陷 30」。

## 目录结构

```
e2e/
├─ playwright.config.ts     # baseURL 默认 4173；workers=1、retries=0、失败 trace+截图
├─ run-shots.mjs            # npm run e2e:shots 的入口（注入 E2E_DEMO=1）
├─ fixtures/
│  ├─ evidence.pdf          # E09/E13 上传证据的默认文件（可 E2E_EVIDENCE_FILE 覆盖）
│  └─ make-evidence-pdf.mjs # 重新生成最小合法 PDF
└─ tests/
   ├─ helpers.ts            # 登录/API 调用/等待/截图/权限辅助
   ├─ e01-login.spec.ts     … e14-audit.spec.ts   # E01–E14
   └─ screenshots.spec.ts   # S01–S28 最终截图
```

## 说明与约定

- 数字断言以 API 直调（如 `/dashboard/summary`、`/workflow/monitor`、分页 total）为准，不写死种子数字。
- 写库用例使用时间戳唯一后缀，标记 `E2E…`；清理失败会在 stdout 打印 `[E13] 残留 …` 等提示，属预期（后续 demo-reset 清除）。
- 失败产物在 `e2e/test-results/`（已 gitignore）；需要重放可 `npx playwright show-trace <trace.zip>`。
- 截图前统一复位窗口/表格横向滚动，避免打开抽屉或点击链接后左侧列被裁切。
- 侧栏会过滤种子中 `visible=0` 的隐藏菜单（表单管理 /wf/form、催办管理 /wf/urge），E03 含对应断言。
