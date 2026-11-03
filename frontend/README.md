# 企业内控与协同运营管理平台 · 前端（M1 骨架）

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
  api/          axios 封装与鉴权接口（request.ts 统一解包 {code,message,data,traceId}）
  config/       应用信息、内置菜单与模块说明（无后端菜单时的兜底）
  directives/   v-perm 按钮权限指令
  layout/       主框架：侧栏、顶栏、多标签页、内容区
  mock/         演示账号、角色菜单/权限与兜底数据（仅演示模式使用）
  router/       路由表、菜单动态注册与登录守卫
  stores/       Pinia：user（登录态/权限/菜单）、app（侧栏/标签页）
  styles/       全局样式
  types/        API 与业务类型
  utils/        鉴权 localStorage、菜单树处理、图标、权限判断、ECharts 按需注册
  views/        登录、工作台、占位页、403/404
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

## M1 范围与未联调项

- 已完成：登录页（真实后端联调通过）、主框架（侧栏/面包屑/用户下拉/多标签页/移动端抽屉）、登录守卫、按菜单动态注册占位路由、直接输入模块地址时补注册路由（避免刷新 404）、`v-perm` 按钮权限、工作台统计卡片与 2 张 ECharts 图、403/404。
- 占位页：各业务模块页面为 M1 骨架占位，展示模块说明与 M2 规划能力，按钮点击仅提示「将在 M2 阶段接入」。
- 仍为静态演示数据：工作台 4 个统计卡片、2 张图表、近期待办列表（M2 接入真实报表接口后替换）。
- 动态路由 M1 采用「静态工作台 + 按菜单注册占位路由」，M2 可按需扩展为完整动态路由与真实页面组件。
