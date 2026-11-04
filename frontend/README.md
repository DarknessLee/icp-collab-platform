# 企业内控与协同运营管理平台 · 前端（M1 骨架 + M2a 系统管理 + M2b 内控域 + M2c 流程中心）

Vue 3.5 + TypeScript + Vite 6 + Element Plus + Pinia + Vue Router 4 + axios + ECharts（按需注册）。

## 快速开始

```powershell
# 安装依赖
npm install

# 开发（http://127.0.0.1:5173 ，/api 代理到 http://127.0.0.1:8080）
npm run dev

# 类型检查
npm run type-check

# 生产构建（vue-tsc + vite build，产物在 dist/）
npm run build

# 预览构建产物（http://127.0.0.1:4173）
npm run preview
```

也可以在仓库根目录使用 `scripts/dev-frontend.ps1`、`scripts/build-frontend.ps1`。

## 环境变量

| 文件 | 变量 | 说明 |
| --- | --- | --- |
| `.env.development` | `VITE_API_BASE=/api/v1` | 开发环境接口前缀（经 Vite 代理） |
| | `VITE_ENABLE_MOCK=true` | 后端未启动时允许演示账号登录 |
| | `VITE_ENABLE_CAPTCHA=false` | 验证码占位开关（默认关闭） |
| `.env.production` | `VITE_API_BASE=/api/v1` | 生产环境接口前缀（由部署侧反向代理） |
| | `VITE_ENABLE_MOCK=false` | 生产环境禁用演示模式 |

## 后端联调账号（真实后端，MySQL 3307 + Spring Boot 8080）

| 角色 | 用户名 | 密码 | 说明 |
| --- | --- | --- | --- |
| 系统管理员 | admin | Icp@2026 | 后端角色 ADMIN + MANAGEMENT |
| 内控管理员 | icpadmin | Icp@2026 | 后端角色 ICP_MANAGER |
| 审计员 | auditor | Icp@2026 | 后端角色 AUDITOR（只读，无写权限） |
| 管理层 | exec | Icp@2026 | 后端角色 MANAGEMENT |
| 部门负责人 | dept01 | Icp@2026 | 后端角色 DEPT_MANAGER |
| 普通员工 | staff01 | Icp@2026 | 后端角色 EMPLOYEE |

后端启动后登录接口返回真实 `menus / permissions`，演示模式自动关闭（页面不再显示「演示模式」标识）。
仅当**后端完全不可达**（网络错误或开发代理 500 空响应）且 `VITE_ENABLE_MOCK=true` 时，才回退到内置演示账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 系统管理员 | admin | Admin@123 |
| 普通员工 | employee | User@123 |

## 目录结构

```
src/
  api/          axios 封装与鉴权、系统管理接口（request.ts 统一解包 {code,message,data,traceId}）
  components/   通用组件（FileUpload 附件上传）
  composables/  组合式函数（usePageList 分页列表）
  config/       应用信息、内置菜单与模块说明（无后端菜单时的兜底）
  directives/   v-perm 按钮权限指令
  layout/       主框架：侧栏、顶栏、多标签页、内容区
  mock/         演示账号、角色菜单/权限与兜底数据（仅演示模式使用）
  router/       路由表、静态系统管理路由、菜单动态注册与登录守卫
  stores/       Pinia：user（登录态/权限/菜单）、app（侧栏/标签页）
  styles/       全局样式
  types/        API 与业务类型
  utils/        鉴权 localStorage、菜单树处理、图标、权限判断、ECharts 按需注册
  views/        登录、工作台、占位页、系统管理 9 页、个人中心、403/404
```

## 接口约定

- 统一前缀 `/api/v1`，响应 `{ code, message, data, traceId }`，`code = 0` 表示成功。
- 请求头携带 `Authorization: Bearer <accessToken>`。
- HTTP 401 → 清理登录态并跳转登录页；403 → 跳转 `/403`。
- 登录：`POST /auth/login`，`body = { username, password }`，`data` 含 `accessToken/refreshToken/expiresIn/user/roles/permissions/menus`。
- 当前用户：`GET /auth/me`，`data` 结构与登录一致（M2 用于刷新用户与菜单）。

## 真实后端联调结果（2026-11-03）

环境：后端 `http://127.0.0.1:8080`（health UP）+ MySQL 3307；前端 `npm run dev` → `http://127.0.0.1:5173`（`/api` 代理到 8080）。Playwright 全流程回归脚本位于 `.runtime\artifacts\.smoke-frontend\real-smoke.mjs`，截图输出到 `.runtime\artifacts\.smoke-frontend\shots\`。账号密码统一 `Icp@2026`，菜单与权限以登录接口实际返回为准。

后端权限种子已按 `docs/05-权限矩阵.md` 重做（db-reset 后回归），各角色实际数据：

| 账号 | 角色 | 一级目录 | 菜单节点 | 权限数 |
| --- | --- | --- | --- | --- |
| admin | ADMIN | 6（系统管理/内控管理/协同办公/流程中心/报表中心/管理驾驶舱） | 38 | 46（系统管理全量 + 业务只读 3 项导出） |
| icpadmin | ICP_MANAGER | 5（无系统管理） | 36 | 63（内控/流程写权限，含流程监控） |
| auditor | AUDITOR | 6（系统管理仅「日志审计」） | 34 | 34（只读 + 导出） |
| exec | MANAGEMENT | 5（无系统管理） | 32 | 30（流程中心含待办/采购/用印/流程监控，只读） |
| dept01 | DEPT_MANAGER | 5（无系统管理） | 31 | 39（制度可新增/编辑，无导出） |
| staff01 | EMPLOYEE | 4（内控管理/协同办公/流程中心/管理驾驶舱） | 20 | 21（制度库只读，缺陷整改本人参与） |

回归结果：**47/47 通过，0 个非预期 console/pageerror**，覆盖：

1. 未登录访问 `/dashboard` → 跳转 `/login?redirect=/dashboard`；
2. admin / Icp@2026 登录成功，侧栏按后端 `menus` 渲染 6 个一级目录；流程中心仅「我的待办/我的已办/我发起」，无「流程监控/采购申请/用印申请」；制度库占位页面包屑「首页 / 内控管理 / 制度库」、`tags = 2`；`v-perm` 正确隐藏「新增/编辑」、仅显示「导出」（业务只读 + 系统管理全量）；
3. 错误密码返回后端 `40101`：提示「用户名或密码错误」；
4. staff01：4 个根目录；内控管理仅「制度库/缺陷管理/整改管理」；制度库无「新增/编辑/导出」；直访 `/system/user` → `/403`，未知路径 → `/404`；
5. icpadmin：5 个目录、无「系统管理」；流程中心含「流程监控」且可打开 `/wf/monitor` 占位页；制度库「新增/编辑」可见；
6. auditor：6 个目录、系统管理下仅「日志审计」（无用户/角色/菜单管理）；制度库无「新增/编辑」写按钮（保留只读导出）；
7. exec：5 个目录、有「流程中心」且含待办/采购/用印/流程监控、无「系统管理」；
8. dept01：5 个目录；制度库「新增/编辑」可见、无「导出」（无 `icp:policy:export`）。

截图：`dashboard-admin.png`、`policy-placeholder-admin.png`、`login-error.png`、`dashboard-staff.png`、`policy-placeholder-staff.png`、`403-staff.png`、`dashboard-icpadmin.png`、`workflow-monitor-icpadmin.png`、`policy-placeholder-icpadmin.png`、`dashboard-auditor.png`、`policy-placeholder-auditor.png`、`dashboard-exec.png`、`policy-placeholder-dept01.png`。

构建：`npm run build` 退出码 0，`vue-tsc` 0 错误，`vite build` 2276 modules / 7.14s，产物约 2 MB。

### 后端菜单/权限契约现状

| # | 原差异 | 现状 |
| --- | --- | --- |
| 1 | 菜单接口为嵌套树，与 `docs/03` 扁平 `parentId` 列表不一致 | **已收敛**：后端保持嵌套树（节点含 `children`，按钮节点 `type=F`/带 `perms`），`docs/03` 已更新为嵌套树契约；前端 `buildMenuTree` 同时兼容嵌套与扁平 |
| 2 | icpadmin 权限与 admin 相同、可见系统管理 | **已修复**：icpadmin 5 个目录、无系统管理、`system:*` 权限 0 条 |
| 3 | auditor/dept01/staff 系统子菜单以根级叶子返回、缺 `/system` 父节点 | **已修复**：auditor 系统管理下仅日志审计；dept01/staff 无系统管理，菜单树祖先节点齐全（orphanChildren=0） |
| 4 | exec 无「流程中心」 | **已修复**：exec 5 个目录含流程中心（待办/已办/采购/用印/我发起/流程监控） |
| 5 | staff01 返回全部 7 个 `icp` 子菜单 | **已修复**：staff01 仅制度库/缺陷管理/整改管理 |
| 6 | admin 拥有业务写权限 | **已修复**：admin 业务只读（仅导出），系统管理全量 |

前端兼容说明：后端菜单子节点 `path` 仍不带前导 `/`、`type` 为 `M`/`C`/`F` 字符串；侧栏与路由注册均已归一化处理，无需后端调整。

## M2a 系统管理与个人中心（真实后端验收，2026-11-03）

覆盖页面：`/system/dept`、`/system/post`、`/system/user`、`/system/role`、`/system/menu`、`/system/dict`、`/system/config`、`/system/log`、`/system/job`、`/profile`；通用组件 `FileUpload`（`POST /files`，DEV 下可用 `/dev/upload` 自测页，生产构建不包含）。

验收环境：后端 8080（MySQL 3307，种子 105 菜单 / 281 role_menu）+ 前端 5173（`npm run dev`）；Playwright 脚本均在 `.runtime\artifacts\.smoke-frontend\`。

### 回归结果

| 用例集 | 脚本 | 结果 |
| --- | --- | --- |
| 6 角色逐页可见性 + v-perm 按钮 + 403 兜底 | `m2a-role-matrix.mjs` | **84/84 PASS**，exit 0 |
| 每模块 CRUD 闭环（含重置密码/状态切换/菜单授权回读/在线会话/改密重登） | `m2a-crud.mjs` | **48/48 PASS**，exit 0 |
| FileUpload：pdf 上传/下载、txt 拒绝 41300 | `m2a-upload.mjs` | **8/8 PASS**，exit 0 |
| 数据范围抽验（admin 30 用户 vs dataScope=2 角色仅本部门 6 用户） | `m2a-scope.mjs` | **10/10 PASS**，exit 0 |
| auditor 日志三 tab + 管理员角色授权抽屉 | `m2a-final-shots.mjs` | **7/7 PASS**，exit 0 |

全部用例**非预期 console error / pageerror = 0**。

### 角色页面可见性（与 docs/05 一致）

| 账号 | 系统管理可见性 | 说明 |
| --- | --- | --- |
| admin | 9 页全部可见 + 个人中心 | 定时任务菜单可见，按钮全量 |
| icpadmin | 系统管理 9 页全部 403 | 无 `system:*` 权限 |
| auditor | 仅「日志审计」可见，三 tab（操作/登录/变更）均可查看 | 无写按钮 |
| exec | 系统管理不可见，直访 9 页均 403 | 无 `system:*` 权限 |
| dept01 | 系统管理不可见，直访 9 页均 403 | 无 `system:*` 权限 |
| staff01 | 系统管理不可见，直访 9 页均 403 | 个人中心仍可访问 |

### 截图（`.runtime\artifacts\.smoke-frontend\shots\m2a\`）

- 页面证据：`system-dept-admin.png`、`system-post-admin.png`、`system-user-admin.png`、`system-role-admin.png`、`system-menu-admin.png`、`system-dict-admin.png`、`system-config-admin.png`、`system-log-admin.png`、`system-job-admin.png`、`profile-admin.png`
- 授权/权限：`system-role-assign-admin.png`（菜单授权抽屉，105 节点）、`system-log-auditor.png`（三 tab）、`system-role-403-icpadmin.png` 等 `system-*-403-<role>.png`
- 附件/数据范围：`file-upload-admin.png`、`system-user-datascope2.png`

### 构建

`npm run build` 退出码 0，`vue-tsc` 0 错误，2308 modules，`vite build` 10.20s；产物 element-plus 839.49 kB / echarts 535.87 kB / vendor 305.02 kB（gzip 后分别 263.61 / 180.34 / 113.58 kB）。

### 接口与已知问题

- 后端实体字段与视图模型在 `src/api/system.ts` 统一双向映射（如 `deptName/deptCode`、`loginTime`、`roleCode`、`menuName/menuType`、`dictLabel/dictValue`、`configValue`、`operTime`）；菜单 `children` 偶尔返回空格字符串，已按空数组兼容。
- 配置修改提交 `{ configValue }`；字典项创建使用 `dictType`；用户创建后可再调用 `PUT /users/{id}/roles` 分配角色（前端创建/编辑弹窗已带角色多选）。
- 字典类型接口未提供删除（`DELETE /dicts/types/{id}` 返回 405），验收过程产生的 3 个 `qa_type_*` 测试类型残留在种子库，建议后端补齐删除接口或下次 db-reset 时清理。
- 页面并发请求中任一接口返回 403 会触发全局「跳转 /403」，属当前统一拦截策略；为 dataScope=2 角色授权时需同时授予页面依赖的全部列表权限（如 `system:dept:list`）。
- 附件下载由 `GET /files/{id}/download` 提供（已验证 200 + `Content-Disposition`），前端 M2a 仅封装上传，下载入口在 M2 文档模块接入。

## M2b 内控域七页（真实后端验收，2026-11-03）

覆盖页面（`src/views/icp/*`）：`/icp/policy` 制度库、`/icp/risk` 风险库、`/icp/control` 控制点、`/icp/matrix` 控制矩阵、`/icp/evaluation` 评价计划与任务（底稿处理）、`/icp/defect` 缺陷管理、`/icp/rectify` 整改管理。

- 通用能力：分类树面板（`CategoryTree`）、部门选择（`DeptPicker`，无 `system:dept:list` 时退化为数字输入，避免 icpadmin 触发全局 403）、附件上传（`FileUpload`，底稿证据/整改证据）、CSV 导出（UTF-8 BOM，按钮受 `v-perm` 控制）、字典兜底（后端字典不可用时用内置中文标签与标签色）。
- 制度：新增草稿 → 编辑 → 提交审批 → 发布（发布时刻 `yyyy-MM-dd HH:mm:ss`）→ 废止 → 草稿删除；详情抽屉含附件与正文。
- 风险：`风险值 = 可能性 × 影响`，高 ≥15 / 中 8–14 / 低 ≤7；新增/编辑/启停/删除。
- 矩阵：覆盖率卡片（风险总数/已覆盖/未覆盖/覆盖率，取 `GET /matrices/coverage` 实际字段）+ 覆盖分布图；新增时前端预检「同一风险 + 控制点只能建立一条矩阵」，后端重复返回业务码。
- 评价：计划新增/编辑/提交审批/开始（生成任务）/完成/关闭/删除草稿；任务开始、底稿处理（评分、结论、抽样/测试过程、测试结果、证据附件、底稿结论）、底稿查看。
- 缺陷：登记/编辑/详情/发起整改/关闭；整改：编辑计划、提交证据（附件+完成日期+说明）、复核通过/驳回、关闭。

### 回归结果（Playwright，`.runtime\artifacts\.smoke-frontend\`）

| 用例集 | 脚本 | 结果 |
| --- | --- | --- |
| 6 角色 7 页可见性 + v-perm 按钮 + 403 兜底 + CSV BOM + 覆盖率卡片 + admin 写接口 403/40300 | `m2b-real-pages.mjs` | **87/87 PASS**，exit 0 |
| 数据权限（admin/dept01/staff01 列表数量、staff01 越权 40300、UI 数字） | `m2b-datascope.mjs` | **18/18 PASS**，exit 0 |
| icpadmin 全链路 CRUD/状态流转（制度/风险/控制点/矩阵/评价计划+任务+底稿/缺陷/整改） | `m2b-real-crud.mjs` | **52/52 PASS**，exit 0 |

全部用例**非预期 console error / pageerror = 0**；构建 `npm run build` 退出码 0，`vue-tsc` 0 错误，`vite build` 10.22s（element-plus 839.99 kB / echarts 535.87 kB / vendor 305.02 kB）。

CSV 导出文件名（UTF-8 BOM 已验证，清单见 `shots\m2b\csv-filenames.txt`）：`制度库导出.csv`、`风险库导出.csv`、`缺陷台账导出.csv`。

### 角色页面与数据权限（干净种子）

| 账号 | 7 页可见性（capability） | 数据量（制度/风险/缺陷/评价计划） |
| --- | --- | --- |
| admin（ADMIN） | 7 页全部可见，内控写按钮全部隐藏（业务只读 + 导出）；直调写接口 403/40300 | 40 / 40 / 30 / 4 |
| icpadmin（ICP_MANAGER） | 7 页全部可写（制度/风险/控制点/矩阵/评价/缺陷/整改） | 全量 |
| auditor（AUDITOR） | 7 页只读 + 导出，无写按钮 | 全量只读 |
| exec（MANAGEMENT） | 7 页只读 + 导出，无写按钮 | 全量只读 |
| dept01（DEPT_MANAGER） | 制度/缺陷可写，风险/控制点/矩阵/评价只读，无导出 | 6 / 6 / 4 / 0（均本部门） |
| staff01（EMPLOYEE） | 制度只读；缺陷/整改仅本人参与；风险/控制点/矩阵/评价 403 | 2 / 2 / 2（/risks、越权详情返回 40300） |

### 截图（`.runtime\artifacts\.smoke-frontend\shots\m2b\`）

- 页面：`policy-admin/icpadmin/auditor/dept01/staff/exec.png`、`risk-*.png`、`control-*.png`、`matrix-*.png`、`evaluation-*`、`defect-*.png`、`rectify-*.png`
- 关键证据：`policy-published.png`、`matrix-coverage-admin.png`（40项/40项/0项/100%）、`matrix-duplicate-warning.png`、`evaluation-submit.png`（底稿处理）、`evaluation-task-submit.png`、`rectify-submit-dialog.png`、`rectify-approved.png`、`defect-closed.png`、`policy-dept01.png`、`defect-staff.png`、`risk-403-staff.png`

### 接口差异与前端兼容（不改后端，仅记录）

- `GET /matrices/coverage` 实际返回 `{ totalRisks, coveredRisks, uncoveredRisks, coverageRate, items[] }`（非 `total/covered/rate/byLevel`），前端 `mapCoverage` 归一化后使用。
- 整改提交 `POST /rectifies/{id}/submit` 的 `evidence` 后端 DTO 为字符串（逗号分隔附件 id），前端在 `api/icp.ts` 以 `join(',')` 适配；评价底稿 `evidence` 为附件 id 数组。
- 矩阵重复冲突后端返回码为 `40000` 或 `40002`（`40002` 为唯一索引冲突）；且唯一索引包含逻辑删除行，历史删除过的「风险+控制点」组合仍不可复用；前端已做提交前预检 + 轮换候选控制点。
- 评价计划「完成评价」要求计划下所有任务已提交；页面完成按钮在任务未完成时返回 40001，属业务状态机约束。
- 数据权限：`staff01` 访问 `/risks`、`/evaluations`、越权详情均返回 `40300`；`dept01` 仅见本部门数据，前端列表数量与后端一致。
- 缺陷列表无 `rectifyCode`（仅 `rectifyId`）；缺陷关闭要求整改存在「通过」记录，正确顺序为 整改提交 → 复核通过 → 缺陷关闭 → 整改单关闭。

## M2c 流程中心（真实后端验收，2026-11-03）

覆盖页面（`src/views/workflow/*`）：`/wf/todo` 我的待办、`/wf/done` 我的已办、`/wf/initiated` 我发起、`/wf/monitor` 流程监控、`/wf/instance` 流程实例（复用 `GET /workflow/monitor` 数据，仅列表与筛选，无统计卡片）、`/wf/definition` 流程定义；申请单 `/wf/purchase` 采购申请、`/wf/seal` 用印申请、`/wf/borrow` 文档借阅（菜单 path 由后端种子下发且无前导 `/`，前端统一 `normalizePath` 归一化；`wf/form`、`wf/urge` 后端置 `visible=0`，前端不设别名）。

- 待办详情抽屉：`GET /tasks/{id}` 的 `formSchema` 只读渲染 + `formData`/`business` 业务字段合并展示（金额两位小数、印章/采购类型枚举映射、附件 chips）、`/instances/{id}/history` 时间线、`/instances/{id}/diagram-highlight` 高亮流程图；页脚同意（意见+附件）/驳回（意见必填）/催办（内容必填），按钮受 `workflow:task:approve|reject|urge` 的 `v-perm` 控制。
- 已办/我发起：分页与流程类型/状态/关键字筛选，状态标签 运行中/已完成/已驳回；我发起的已驳回实例支持「修改后重新提交」（`POST /instances/{id}/resubmit`）。
- 监控：运行中/已完成/已驳回/超时（>48h）统计卡片 + 超时筛选 + 实例抽屉；流程定义 6 条列表与 bpmn-js 只读流程图弹窗。
- 申请单：采购（标题/采购类型/供应商/金额/预算/用途，金额必填且不得超预算）提交后提示「已提交审批，可在流程中心跟踪进度」；用印（印章类型/日期/事由/附件）；借阅（文档 ID 须在 `coll_doc` 存在/起止日期/事由）。创建时后端直接发起流程并在 `data.workflow` 返回 `processInstanceId/taskId`。
- 制度/评价提交审批：`policyApi.submit`、`evaluationApi.submit` 若返回 `processInstanceId` 则提示「已提交审批，可在流程中心跟踪审批进度」，不再前端二次发布。
- bpmn-js 仅只读 viewer，动态 `import('bpmn-js/lib/Viewer')`；`vite.config.ts` 单独拆 `bpmn` chunk（140.08 kB / gzip 38.39 kB）。真实 BPMN XML 无 `BPMNDiagram/DI` 坐标，`WorkflowDiagram` 前端按节点顺序自动生成水平布局坐标后渲染，`fit-viewport` 失败时回退 1:1 缩放。

### 真实后端回归（2026-11-03，clean seed + seed-flow 后）

| 脚本 | 用例 | 结果 |
| --- | --- | --- |
| `.runtime\artifacts\.smoke-frontend\m2c-real-pages.mjs` | 6 角色 × 9 页面访问/按钮/403 | **69/69 PASS** |
| `.runtime\artifacts\.smoke-frontend\m2c-real-flow.mjs` | 采购发起→审批→完成、用印驳回→重提、催办、bpmn 高亮、监控/实例/定义、403 | **55/55 PASS** |
| `.runtime\artifacts\.smoke-frontend\m2c-real-extra.mjs` | 采购驳回→重提→完成、文档借阅发起→审批→完成 | **28/28 PASS** |
| `.runtime\artifacts\.smoke-frontend\m2c-final-shots.mjs` | 最终截图与页面复核 | **21/21 PASS** |

非预期 console/pageerror = 0；`npm run build` exit 0（vue-tsc 0 错误，vite 12.49s；element-plus 839.99 kB / echarts 535.87 kB / bpmn 140.08 kB / vendor 354.24 kB）。

实测 6 角色（回归结束时，权限数为登录返回 permissions 条数）：admin 86（只读，无 add/approve，可监控/实例/定义）、icpadmin 107（全量）、auditor 53（只读）、exec 51（只读 + 待办审批）、dept01 70（待办/已办/申请单/审批）、staff01 33（待办/已办/申请单/审批发起节点）。待办/已办/我发起实测：admin 0/0/0、icpadmin 3/30/18、auditor 0/0/0、exec 5/0/0、dept01 5/21/0、staff01 2/20/15；监控统计 running 7 / completed 23 / rejected 3 / overdue 0 / total 33（回归期间 Lead 验收脚本也写入 verify 单据，数字为结束时快照）。

已跑通闭环：采购 发起（员工）→ 发起人确认 → 部门负责人审批 → 管理层审批 → COMPLETED；采购 发起 → 部门负责人驳回 → 员工「重新提交」→ RUNNING → 审批至 COMPLETED；用印 发起 → 驳回 → 重新提交（RUNNING）；文档借阅 发起 → 部门负责人 → 内控管理员审批 → COMPLETED；待办抽屉表单/历史/流程图（当前节点蓝框、已完成绿框）、催办提示、已办与我发起状态标签。

本轮创建的业务单（供验收后 reset）：CG-2026-022、CG-2026-031、CG-2026-032（采购），YY-2026-017（用印），借阅单 3394（docId 3301）。另有 Lead 验收脚本生成的 `Lead verify *` 单据（CG-2026-023~030、YY-2026-014~016、借阅 3391~3393）。

### 截图（`.runtime\artifacts\.smoke-frontend\shots\m2c\`）

最终批次：`final-todo-form.png`、`final-todo-history.png`、`final-todo-diagram.png`、`final-{todo,done,initiated,monitor,instance,definition,purchase,seal,borrow}-icpadmin.png`、`final-monitor-admin.png`、`final-instance-admin.png`、`final-monitor-403-staff01.png`；回归批次：`{todo,done,initiated,monitor,purchase,seal,borrow}-{icpadmin,admin,auditor,exec,dept01,staff01}.png`、`purchase-submit-form`、`seal-submit-form`、`borrow-submit-form`、`flow-approve-drawer-*`、`flow-reject-drawer-*`、`flow-diagram-staff01`、`flow-initiated-running/completed/rejected`、`flow-resubmitted`。

### 已知遗留

- 后端 BPMN 无 DI 坐标：前端已自动布局兜底，建议后端导图时输出标准 `BPMNDiagram`。
- `exec`（MANAGEMENT）实际有 `workflow:task:approve/reject/urge`，可审批待办，与「管理层只读」口径存在偏差，按真实 permissions 动态显示。
- 借阅申请需 `coll_doc` 中已存在文档 ID，前端暂以「文档 ID」输入 + 说明提示，M3 文档中心开放后替换为文档选择器。


## M2d 协同运营（前端 mock 联调完成，真实后端待就绪）

状态：2026-11-03，M2d 前端六页已按 docs/03 §6/§6.1、docs/04 DDL、DECISIONS D36/D37/D38 实现，`npm run build` 通过（vue-tsc 0 错误），Playwright mock 冒烟 **43/43 PASS**（console/pageerror 0）。后端 collab 模块尚未部署，当前以 dev-only mock 联调，待 Lead 通知「接口就绪」后重登 6 角色跑真实回归。

### 页面与能力

| 菜单 | 页面 | 主要能力 |
| --- | --- | --- |
| 任务管理 | `views/collab/task/index.vue` | 状态/优先级/仅逾期/只看我的/关键词筛选；新建/编辑（任务类型 1 计划/2 任务/3 子任务、优先级 1 高/2 中/3 低、负责人、计划起止、提醒时间）；开始/完成/取消；子任务；评论抽屉；手动提醒；删除；CSV 导出 |
| 文档中心 | `views/collab/doc/index.vue` | 分类树（新增分类）+ 文档列表；上传（FileUpload）、编辑、发布、归档、删除；下载 blob；权限抽屉按 `target_type 0 全员/1 角色/2 用户/3 部门` + `perm_type 1 查看/2 下载/3 编辑` 多行三元组维护，PUT 全量覆盖 |
| 文档借阅 | `views/collab/doc/borrow.vue` | 列表/详情；发起借阅（选择已发布文档 + 起止日期 + 事由）；已通过可下载；「归还」按申请本人或 ADMIN 显示（无独立权限串） |
| 公告通知 | `views/collab/notice/index.vue` | 未读 badge；状态/仅未读/关键词筛选；新建/编辑（noticeType 1 通知/2 公告/3 制度宣贯、scope 全员/部门/角色 + scopeDeptIds/scopeRoles）；发布/关闭/删除；查看即已读 |
| 消息中心 | `views/collab/message/index.vue` | 类型（1 系统/2 审批/3 任务/4 公告/5 预警）、阅读状态筛选；勾选批量已读/全部已读；SSE 实时（顶部「实时连接中/重连中」，新消息通知 + 未读数刷新）；`messageType=3 && bizType=REMIND` 显示「任务提醒」 |
| 日程会议 | `views/collab/schedule/index.vue` | 月历（42 格、今日高亮、每格最多 3 条 + N）、列表；新建/编辑（类型 1 会议/2 提醒/3 日程、起止、地点）；取消（status=3）；标签 0 待开始/1 进行中/2 已结束/3 已取消 |

### 契约落实要点

- 任务写入映射 `title → taskTitle`；文档 `title → docName`、`summary → description`；公告 `title → noticeTitle`；日程 `title → scheduleTitle`、`type → scheduleType`；返回侧防御映射兼容 DDL 字段名。
- 文档下载校验：已发布 → 权限命中 → 该文档审批通过借阅单兜底；40300 前端提示「无下载权限，可发起借阅后下载」。
- SSE：`EventSource` 携带 `access_token`（不打印 token），断线静默指数退避 1s/2s/4s…上限 30s，不产生 console error。
- 权限全部走 `v-perm` / `useUserStore().hasPermission()`，不硬编码角色；路由 `meta.permAny` 命中任一即可访问，无权限由守卫跳 403。

### mock 回归与截图

- 脚本：`.runtime\artifacts\.smoke-frontend\m2d-mock-smoke.mjs`（Playwright 全量拦截 `/api/v1/**`）→ **43/43 PASS**。
- 截图：`.runtime\artifacts\.smoke-frontend\shots\m2d\`：`mock-task-list.png`、`mock-doc-list.png`、`mock-doc-perms-drawer.png`、`mock-borrow-list.png`、`mock-notice-list.png`、`mock-message-list.png`、`mock-message-403.png`、`mock-schedule-month.png`。
- 构建：`npm run build` exit 0，vue-tsc 0 错误，vite 约 13s（element-plus 840.00kB / echarts 535.87kB / bpmn 140.08kB / vendor 354.24kB）。

### 待真实后端回归

- 6 角色菜单/403 矩阵、任务全链（新建→开始→完成/取消、子任务、评论、提醒）、文档上传→发布→权限→下载→归档、借阅申请→审批→通过→下载→归还、公告发布→已读/未读数、消息 SSE 实时 + 批量已读、日程月历/取消。
- 截图产出到 `shots\m2d\`（final-* 最终版），并回填本 README。

## M2e 报表与预警中心（真实后端验收，2026-11-04）

状态：2026-11-04，M2e 前端工作台改造 + 5 个报表/预警页已按 docs/03 §7/§7.1、docs/01 §2 W01–W10 实现并完成真实后端回归：`m2e-real-pages.mjs` **126/126 PASS**（6 角色页面可见性/菜单/403）、`m2e-real-consistency.mjs` **47/47 PASS**（页面数字与 API 直调逐一对比、trend 边界、缺陷筛选、预警 4 tab/count/空态），两脚本 console/pageerror 均为 0；`npm run build` exit 0，vue-tsc 0 错误，vite 12.75s。

### 页面与能力

| 菜单 | 页面 | 主要能力 |
| --- | --- | --- |
| 工作台 | `views/dashboard/index.vue` | 8 指标卡（制度总/有效、风险总/高、覆盖率、缺陷/未关闭、整改率、待办、超期任务、未读消息）+ 快捷入口按权限过滤 + 近 6 月双轴趋势（新增制度/缺陷柱 + 流程均耗时线）+ 骨架/错误重试/刷新；数据来自 `/dashboard/summary`、`/dashboard/trend`，替换原静态演示数据 |
| 内控概览 | `views/report/overview.vue` | 覆盖率大数字 + dashboard 进度 + 已覆盖/未覆盖；部门/分类覆盖表与分类堆叠柱；部门下拉（需 `system:dept:list`）+ 年度筛选 |
| 制度与风险 | `views/report/policy.vue` | dim=level/category/dept 切换；风险总数/高/中/低 4 卡；环形图 + 明细（占比 1 位小数） |
| 缺陷统计 | `views/report/defect.vue` | 严重程度/状态筛选；缺陷总数/整改总数/已通过/完成率卡；严重程度×状态堆叠柱 + 明细；按部门整改率表 + 月度趋势双轴图 |
| 任务与流程 | `views/report/task.vue` | 任务状态分布 5 卡（总数/待开始/进行中/已完成/逾期，来自 `/reports/tasks`）+ 近 3/6/12 月；6 流程平均/P95 耗时条形图 + 明细；月度完成趋势（均耗时线 + 实例数柱） |
| 预警中心 | `views/report/alert.vue` | `/alerts/count` 4 类计数卡（任务逾期/制度到期/高风险缺陷/流程超时）；`/alerts` 按 type 分页表、级别标签、剩余/逾期天数；行点击跳转任务/制度/缺陷/流程页 |

### 契约落实要点

- 防御性归一化：`api/report.ts` 对空值/缺字段给 0，`rate` 兼容 0–1 与百分数并 clamp 到 [0,1]，页面统一格式化为 1 位小数或 `%`，空态不出现 `NaN`/负数。
- 权限不新增串：报表页沿用 `report:icp:view`、`report:risk:view`、`report:rectify:view`、`report:process:view`、`report:alert:view`，工作台沿用 `dashboard:workbench:view`/`dashboard:todo:view`；路由 `meta.permAny` + 守卫跳 403。
- 无新增依赖：图表复用现有 `utils/echarts.ts` 按需注册封装（Bar/Line/Pie + Grid/Tooltip/Legend）。
- `/reports/tasks` 已由后端补齐：`views/report/task.vue` 展示任务状态 5 卡；接口不可用时自动隐藏任务卡并保留流程耗时展示，不报错、不估造数据。

### 真实回归与截图

- 脚本（均在真实后端 8080 + dev server 5173 上运行）：
  - `.runtime\artifacts\.smoke-frontend\m2e-real-pages.mjs` → **126/126 PASS**：6 角色重登，工作台 8 卡可见；侧栏「报表中心」与 `report:*` 一致；5 报表页有权限显示标题/表格/无 NaN、无权限跳 `/403`；console/pageerror 0。
  - `.runtime\artifacts\.smoke-frontend\m2e-real-consistency.mjs` → **47/47 PASS**：W01 summary 8 卡逐项相等；W02 覆盖率 100.0%/7 部门/8 分类；W03 dim=level 40/12/18/10、category 8 类、dept 7 类；W04 缺陷 30/整改率 40.0%/筛选 重大+已关闭=4；W05 tasks total/状态计数/逾期与 API 相等、6 流程 p95≥avg；W06 预警 4 卡 5/0/6/0=11、列表 total 一致、制度到期空态、高风险缺陷有数据；trend months=1/12 边界；rate 1 位小数；console/pageerror 0。
  - `.runtime\artifacts\.smoke-frontend\m2e-real-shots.mjs`：staff01 直访 `/report/alert` → `/403`、预警 4 tab 与空态截图。
- 6 角色权限快照（真实后端）：admin 95（report 6）、icpadmin 137（6）、auditor 62（6）、exec 60（6）、dept01 104（6）、staff01 51（0，仅 dashboard 两项）；staff01 报表菜单不显示、5 个 `/report/*` 全部 403。
- 关键数字（干净种子基线，admin 直调 API 与页面逐项相等）：制度 40/有效 30、风险 40/高 12、覆盖率 100.0%（40/40）、缺陷 30/未关闭 20、整改 25/已通过 10/40.0%、任务 total 80（0:8 / 1:12 / 2:60 / 3:0，逾期 5）、预警 5/0/6/0=11。
- 截图：`.runtime\artifacts\.smoke-frontend\shots\m2e\` 共 43 张 `real-*.png`（干净基线 2026-11-04 10:10–10:14）：`real-dashboard-{6 角色}.png`、`real-{overview,policy,defect,task,alert}-{5 角色}.png`、`real-consistency-*.png`、`real-alert-tab-{task-overdue,policy-expiring,defect-high-risk,process-overdue}.png`、`real-403-staff.png`、`real-empty-alert.png`，以及 mock 阶段 `m2e-*.png`。
- 构建：`npm run build` exit 0，vue-tsc 0 错误，vite 12.75s（element-plus 840.00kB / echarts 535.87kB / bpmn 140.08kB / vendor 354.24kB）。

## M1 范围与未联调项

- 已完成：登录页（真实后端联调通过）、主框架（侧栏/面包屑/用户下拉/多标签页/移动端抽屉）、登录守卫、按菜单动态注册占位路由、直接输入模块地址时补注册路由（避免刷新 404）、`v-perm` 按钮权限、工作台统计卡片与 2 张 ECharts 图、403/404。
- 占位页：各业务模块页面为 M1 骨架占位，展示模块说明与 M2 规划能力，按钮点击仅提示「将在 M2 阶段接入」。
- 仍为静态演示数据：工作台 4 个统计卡片、2 张图表、近期待办列表（M2 接入真实报表接口后替换）。
- 动态路由 M1 采用「静态工作台 + 按菜单注册占位路由」，M2 可按需扩展为完整动态路由与真实页面组件。
