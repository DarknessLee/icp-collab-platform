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

环境：后端 `http://127.0.0.1:8080`（health UP）+ MySQL 3307；前端 `npm run dev` → `http://127.0.0.1:5173`（`/api` 代理到 8080）。Playwright 全流程回归脚本位于仓库外 `D:\MyCodes\.smoke-frontend\real-smoke.mjs`，截图输出到 `D:\MyCodes\.smoke-frontend\shots\`。账号密码统一 `Icp@2026`，菜单与权限以登录接口实际返回为准。

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

验收环境：后端 8080（MySQL 3307，种子 105 菜单 / 281 role_menu）+ 前端 5173（`npm run dev`）；Playwright 脚本均在仓库外 `D:\MyCodes\.smoke-frontend\`。

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

### 截图（`D:\MyCodes\.smoke-frontend\shots\m2a\`）

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

### 回归结果（Playwright，仓库外 `D:\MyCodes\.smoke-frontend\`）

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

### 截图（`D:\MyCodes\.smoke-frontend\shots\m2b\`）

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
| `D:\MyCodes\.smoke-frontend\m2c-real-pages.mjs` | 6 角色 × 9 页面访问/按钮/403 | **69/69 PASS** |
| `D:\MyCodes\.smoke-frontend\m2c-real-flow.mjs` | 采购发起→审批→完成、用印驳回→重提、催办、bpmn 高亮、监控/实例/定义、403 | **55/55 PASS** |
| `D:\MyCodes\.smoke-frontend\m2c-real-extra.mjs` | 采购驳回→重提→完成、文档借阅发起→审批→完成 | **28/28 PASS** |
| `D:\MyCodes\.smoke-frontend\m2c-final-shots.mjs` | 最终截图与页面复核 | **21/21 PASS** |

非预期 console/pageerror = 0；`npm run build` exit 0（vue-tsc 0 错误，vite 12.49s；element-plus 839.99 kB / echarts 535.87 kB / bpmn 140.08 kB / vendor 354.24 kB）。

实测 6 角色（回归结束时，权限数为登录返回 permissions 条数）：admin 86（只读，无 add/approve，可监控/实例/定义）、icpadmin 107（全量）、auditor 53（只读）、exec 51（只读 + 待办审批）、dept01 70（待办/已办/申请单/审批）、staff01 33（待办/已办/申请单/审批发起节点）。待办/已办/我发起实测：admin 0/0/0、icpadmin 3/30/18、auditor 0/0/0、exec 5/0/0、dept01 5/21/0、staff01 2/20/15；监控统计 running 7 / completed 23 / rejected 3 / overdue 0 / total 33（回归期间 Lead 验收脚本也写入 verify 单据，数字为结束时快照）。

已跑通闭环：采购 发起（员工）→ 发起人确认 → 部门负责人审批 → 管理层审批 → COMPLETED；采购 发起 → 部门负责人驳回 → 员工「重新提交」→ RUNNING → 审批至 COMPLETED；用印 发起 → 驳回 → 重新提交（RUNNING）；文档借阅 发起 → 部门负责人 → 内控管理员审批 → COMPLETED；待办抽屉表单/历史/流程图（当前节点蓝框、已完成绿框）、催办提示、已办与我发起状态标签。

本轮创建的业务单（供验收后 reset）：CG-2026-022、CG-2026-031、CG-2026-032（采购），YY-2026-017（用印），借阅单 3394（docId 3301）。另有 Lead 验收脚本生成的 `Lead verify *` 单据（CG-2026-023~030、YY-2026-014~016、借阅 3391~3393）。

### 截图（`D:\MyCodes\.smoke-frontend\shots\m2c\`）

最终批次：`final-todo-form.png`、`final-todo-history.png`、`final-todo-diagram.png`、`final-{todo,done,initiated,monitor,instance,definition,purchase,seal,borrow}-icpadmin.png`、`final-monitor-admin.png`、`final-instance-admin.png`、`final-monitor-403-staff01.png`；回归批次：`{todo,done,initiated,monitor,purchase,seal,borrow}-{icpadmin,admin,auditor,exec,dept01,staff01}.png`、`purchase-submit-form`、`seal-submit-form`、`borrow-submit-form`、`flow-approve-drawer-*`、`flow-reject-drawer-*`、`flow-diagram-staff01`、`flow-initiated-running/completed/rejected`、`flow-resubmitted`。

### 已知遗留

- 后端 BPMN 无 DI 坐标：前端已自动布局兜底，建议后端导图时输出标准 `BPMNDiagram`。
- `exec`（MANAGEMENT）实际有 `workflow:task:approve/reject/urge`，可审批待办，与「管理层只读」口径存在偏差，按真实 permissions 动态显示。
- 借阅申请需 `coll_doc` 中已存在文档 ID，前端暂以「文档 ID」输入 + 说明提示，M3 文档中心开放后替换为文档选择器。


## M1 范围与未联调项

- 已完成：登录页（真实后端联调通过）、主框架（侧栏/面包屑/用户下拉/多标签页/移动端抽屉）、登录守卫、按菜单动态注册占位路由、直接输入模块地址时补注册路由（避免刷新 404）、`v-perm` 按钮权限、工作台统计卡片与 2 张 ECharts 图、403/404。
- 占位页：各业务模块页面为 M1 骨架占位，展示模块说明与 M2 规划能力，按钮点击仅提示「将在 M2 阶段接入」。
- 仍为静态演示数据：工作台 4 个统计卡片、2 张图表、近期待办列表（M2 接入真实报表接口后替换）。
- 动态路由 M1 采用「静态工作台 + 按菜单注册占位路由」，M2 可按需扩展为完整动态路由与真实页面组件。
