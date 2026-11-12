# 企业内控与协同运营管理平台

> 面向企业内控与合规管理的一体化平台：以「制度 → 风险 → 控制矩阵 → 内控评价 → 缺陷整改」为主闭环，融合流程审批、协同办公、文档管理与运营看板。

**版本**：V1.0　|　**形态**：B/S Web（前后端分离，PC 优先、响应式兼容移动浏览器）　|　**角色**：系统管理员 / 内控管理员 / 部门负责人 / 普通员工 / 审计员 / 管理层

## 项目简介

企业内控工作通常散落在制度文档、风险台账、检查表和整改跟踪表里，制度、风险、控制措施与整改结果之间缺乏可追溯的关联，审批与协同又依赖线下流转。本平台把这些环节收敛到一套系统内：

- **一条主线**：制度发布 → 风险识别与评分 → 控制矩阵覆盖 → 评价计划与底稿 → 缺陷登记 → 整改复核关闭，环环留痕、可追溯到具体制度与责任人；
- **一套审批**：基于 Flowable 7 的流程中心，内置制度发布、缺陷整改、评价计划、文档借阅、采购申请、用印申请 6 条流程，支持金额分级审批、抄送、催办与流程监控；
- **一体化协同**：任务、文档、借阅、公告、站内信、日程、会议纪要集中在同一工作台，配合消息实时推送；
- **一屏总览**：工作台指标卡、内控/制度/缺陷/任务/预警 5 类报表、4 类预警规则与定时扫描，另有全屏数据大屏用于汇报展示；
- **一套权限**：RBAC + 数据权限（本人 / 本部门 / 本部门及下级），6 类角色按菜单与按钮粒度授权，操作与登录全量审计。

## 核心功能

| 模块 | 能力 |
| --- | --- |
| 组织与权限 | 部门、岗位、用户、角色、菜单、字典、参数、定时任务 9 个管理页面；RBAC + 数据权限；登录安全（JWT、失败锁定、在线会话与强制下线）；操作/登录/变更审计；附件中心 |
| 内控业务 | 制度库（分类、版本、发布审批）、风险库（概率 × 影响评分定级）、控制点与控制矩阵（覆盖率）、评价计划 → 任务 → 底稿、缺陷登记 → 整改 → 复核关闭 |
| 流程中心 | Flowable 7 内嵌，6 条内置 BPMN；待办 / 已办 / 我发起、同意 / 驳回 / 重提 / 催办、流程监控与超时提醒、只读 BPMN 高亮、审批历史留痕；采购申请按金额分级（≤5000 部门审批，>5000 追加管理层） |
| 协同运营 | 任务中心（子任务、评论、提醒）、文档中心（目录、权限、版本、下载、PDF/图片在线预览）、文档借阅、公告（范围与已读）、站内信（SSE 实时推送）、日程月历、会议纪要 |
| 报表与预警 | 工作台 8 个指标卡 + 趋势图；内控概览、制度、缺陷、任务、预警 5 个报表页；4 类预警规则与 5 个定时扫描任务；图表点击下钻列表；全屏数据大屏 |
| 审计与追溯 | 操作日志（含 traceId、请求参数脱敏）、登录日志、变更留痕；关键业务单据全程状态流转记录 |

## 技术架构

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3.5 + TypeScript 5.7 + Vite 6.4 + Element Plus 2.14 + Pinia 2.3 + Vue Router 4.6 + axios 1.21 + ECharts 5.6；bpmn-js 17.11 只读流程图 |
| 后端 | Java 21 LTS + Spring Boot 3.3.4 + Spring Security + JWT（jjwt）+ MyBatis-Plus + Flyway；Flowable 7.1.0（6 条内置 BPMN） |
| 数据库 | MySQL 8.0（兼容 5.7+），Flyway V1–V4 管理 43 张业务表；Flowable `ACT_*` 39 张由引擎自建 |
| 存储与缓存 | 本地磁盘附件存储（`FileStorage` 接口，可切换 S3/MinIO）+ 进程内 Caffeine 缓存（`CacheService` 接口，可切换 Redis） |
| 接口文档 | springdoc OpenAPI 3，在线地址 `/swagger-ui/index.html` |
| 部署 | Docker Compose（MySQL / 后端 / Nginx / 前端 + 备份恢复脚本）+ 本地 PowerShell 一键脚本 |

## 界面预览

> 截图来自系统内置示例数据（60 个流程实例、40 条待办、52 条制度、40 条风险、30 条缺陷、20 条会议纪要），登录账号 `admin`。完整 30 张与逐张说明见 [`docs/screenshots/INDEX.md`](docs/screenshots/INDEX.md)。

### 登录与工作台

| 登录页 | 系统管理员工作台（8 卡 + 趋势） |
| --- | --- |
| ![登录页](docs/screenshots/s01-login.png) | ![工作台](docs/screenshots/s02-dashboard.png) |

### 内控业务闭环（制度 → 风险 → 矩阵 → 评价 → 缺陷 → 整改）

| 制度库（分类/版本/发布审批） | 制度详情 + 审批历史 |
| --- | --- |
| ![制度库](docs/screenshots/s04-policy-list.png) | ![制度详情](docs/screenshots/s26-policy-detail.png) |

| 风险库（概率×影响评分定级） | 控制矩阵（覆盖率） |
| --- | --- |
| ![风险库](docs/screenshots/s05-risk-list.png) | ![控制矩阵](docs/screenshots/s06-matrix.png) |

| 评价计划 → 任务 → 底稿 | 缺陷登记 → 整改 → 复核关闭 |
| --- | --- |
| ![评价](docs/screenshots/s07-evaluation.png) | ![缺陷台账](docs/screenshots/s08-defect.png) |

| 整改复核（缺陷关闭留痕） | |
| --- | --- |
| ![整改复核](docs/screenshots/s09-rectify.png) | |

### 流程中心（Flowable 7，6 条内置流程）

| 待办中心 | 待办详情（表单 + 审批历史 + BPMN 高亮） |
| --- | --- |
| ![待办](docs/screenshots/s10-todo.png) | ![待办详情](docs/screenshots/s27-todo-detail.png) |

| 流程监控（运行/超时/催办） | 采购申请（金额分级审批） |
| --- | --- |
| ![流程监控](docs/screenshots/s11-monitor.png) | ![采购申请](docs/screenshots/s12-purchase.png) |

| 用印申请 | |
| --- | --- |
| ![用印申请](docs/screenshots/s13-seal.png) | |

### 协同运营（任务 / 文档 / 公告 / 消息 / 日程 / 会议）

| 任务中心 | 文档中心（目录/权限/版本） |
| --- | --- |
| ![任务中心](docs/screenshots/s15-collab-task.png) | ![文档中心](docs/screenshots/s16-doc-center.png) |

| 文档借阅审批归还 | 公告通知（范围/已读） |
| --- | --- |
| ![文档借阅](docs/screenshots/s14-borrow.png) | ![公告](docs/screenshots/s17-notice.png) |

| 消息中心（SSE 实时推送） | 日程月历 |
| --- | --- |
| ![消息中心](docs/screenshots/s18-message.png) | ![日程月历](docs/screenshots/s19-schedule.png) |

| 会议纪要（列表 + 详情/纪要） | |
| --- | --- |
| ![会议纪要](docs/screenshots/s29-meeting.png) | |

### 报表与预警

| 内控概览报表 | 缺陷统计（严重程度×状态） |
| --- | --- |
| ![内控概览](docs/screenshots/s03-report-overview.png) | ![缺陷统计](docs/screenshots/s20-report-defect.png) |

| 预警中心（4 类规则 + 5 个定时扫描任务） | |
| --- | --- |
| ![预警中心](docs/screenshots/s21-alert.png) | |

### 数据大屏

| 运营驾驶舱（`/screen`，6 KPI + 6 面板，60s 刷新） | |
| --- | --- |
| ![数据大屏](docs/screenshots/s30-screen.png) | |

### 系统管理与权限（RBAC + 数据权限 + 审计）

| 角色菜单授权（558 条角色菜单） | 审计操作日志（含 traceId 追踪） |
| --- | --- |
| ![角色授权](docs/screenshots/s23-role-menu.png) | ![审计日志](docs/screenshots/s22-audit-log.png) |

| 个人中心 | 越权访问 403（6 角色对比） |
| --- | --- |
| ![个人中心](docs/screenshots/s24-profile.png) | ![403](docs/screenshots/s25-forbidden-403.png) |

### 移动端适配

| 窄屏侧栏抽屉（响应式，≤768px） | |
| --- | --- |
| ![移动端](docs/screenshots/s28-mobile-sidebar.png) | |

## 快速开始

前置环境：JDK 21、Maven 3.9、Node.js 18+、MySQL 8.0（本机如只有 5.7 也可，脚本会自动选用；仅复用其 `mysqld` 二进制，不占用系统 3306 端口）。

```powershell
# 1. 一键启动：项目内 MySQL 实例（3307）+ 后端（8080）+ 前端预览（4173）
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1

# 2. 可选：重置为内置示例数据（60 个流程实例 / 40 条待办 / 20 条催办）
powershell -ExecutionPolicy Bypass -File scripts\reset-data.ps1
```

- 访问 `http://127.0.0.1:4173`；开发模式用 `scripts\dev-frontend.ps1`（5173，热更新）。
- 内置账号（密码统一 `Icp@2026`）：`admin`（系统管理员）、`icpadmin`（内控管理员）、`auditor`（审计员）、`exec`（管理层）、`dept01`–`dept06`（部门负责人）、`staff01`–`staff20`（普通员工）。
- 后端健康检查 `/actuator/health`，接口文档 `/swagger-ui/index.html`。
- 停止服务：`scripts\stop-local.ps1`（加 `-WithDb` 一并停止数据库）。
- 备份与恢复：`scripts\backup.ps1` → `scripts\restore.ps1 -Path <备份目录>`。

## 测试与质量保障

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1        # 后端单测 + 前端构建
powershell -ExecutionPolicy Bypass -File scripts\test-api.ps1 -Reset   # 全量 API 回归（375 例）
powershell -ExecutionPolicy Bypass -File scripts\test-e2e.ps1     # Playwright 端到端（31 项）
powershell -ExecutionPolicy Bypass -File scripts\screenshots.ps1 -Take  # 采集界面截图（30 张）
powershell -ExecutionPolicy Bypass -File scripts\check-docs.ps1   # 文档一致性检查
powershell -ExecutionPolicy Bypass -File scripts\verify.ps1       # 全链路一键自检
```

当前质量基线：JUnit **177** 项通过、API 回归 **375** 例通过、Playwright **31** 项通过、界面截图 **30** 张。

## 部署（Docker Compose）

`deploy/` 提供完整容器化部署文件：MySQL、后端、Nginx 前端、备份/恢复与初始化 SQL，环境变量集中在 `.env`；Nginx 负责 `/api/` 反向代理与前端静态资源。

详细步骤见 [`deploy/README.md`](deploy/README.md) 与 [docs/13-部署与运维手册.md](docs/13-部署与运维手册.md)（含端口、配置项、备份恢复、故障排查与升级回滚）。

## 目录结构

```text
icp-collab-platform/
├─ backend/                 # Spring Boot 3.3.4，包结构 com.icp：common / system / icp / workflow / collab / report
│  └─ src/main/resources/   # db/migration（V1 schema / V2 seed_base / V3 seed_business / V4 coll_meeting，43 张业务表）+ processes/（6 条 BPMN）
├─ frontend/                # Vue 3 + Vite：登录、主框架、工作台、系统管理 9 页、内控 7 页、流程中心 9 页、协同 7 页、报表 5 页、数据大屏
├─ e2e/                     # Playwright：E01–E19 端到端用例 + 截图采集
├─ scripts/                 # 启动/停止、数据重置、备份恢复、构建、API 与 E2E 测试、截图、文档检查
├─ docs/                    # 需求与设计文档、部署运维手册、用户手册、接口契约、数据模型、权限矩阵、验收报告、截图、使用说明
├─ deploy/                  # Docker Compose、Nginx、初始化与备份恢复脚本
└─ README.md
```

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/00-项目画像.md](docs/00-项目画像.md) | 项目目标、范围、角色与不做清单 |
| [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md) | 交付计划、验收口径、E2E 用例与截图清单 |
| [docs/02-方案差距表与风险.md](docs/02-方案差距表与风险.md) | 需求对照、假设、风险与待确认事项 |
| [docs/03-接口契约.md](docs/03-接口契约.md) · [docs/04-数据模型与种子.md](docs/04-数据模型与种子.md) · [docs/05-权限矩阵.md](docs/05-权限矩阵.md) | 接口契约、数据模型与种子数据、角色权限矩阵 |
| [docs/13-部署与运维手册.md](docs/13-部署与运维手册.md) | 环境要求、部署步骤、配置说明、备份恢复与运维 |
| [docs/14-用户手册.md](docs/14-用户手册.md) | 面向业务用户的功能与操作说明 |
| [docs/18-企业内控与协同运营管理平台-使用说明-图文版.docx](docs/18-企业内控与协同运营管理平台-使用说明-图文版.docx) | 图文版使用说明（Word，11 章 + 2 附录，30 张截图） |
| [docs/screenshots/INDEX.md](docs/screenshots/INDEX.md) | 界面截图索引（S01–S30） |
| [STATUS.md](STATUS.md) · [DECISIONS.md](DECISIONS.md) · [PLAN-MATRIX.md](PLAN-MATRIX.md) | 项目状态、决策记录、能力矩阵 |

各阶段验收报告见 `docs/06`–`docs/12`、`docs/16`。

## 范围与后续规划

**当前版本已覆盖**：组织权限与登录审计、制度/风险/控制矩阵、内控评价与缺陷整改闭环、6 条内置审批流（采购金额分级）、任务/文档/公告/消息/日程/会议纪要、看板与预警、图表下钻、数据大屏、容器化部署文件与完整测试与文档。

**后续规划（v1.1+）**：多租户、真实 Redis/MinIO/Elasticsearch、OAuth2/LDAP 单点登录、电子签名、BPMN 在线设计器与低代码表单、合同/采购/费用业务插件、独立移动端、Kubernetes 与监控栈。

## 开发约定

- 源码统一 UTF-8 编码；仓库内 PowerShell 脚本保持 ASCII，兼容 Windows PowerShell 5.1。
- 运行数据（数据库、附件、日志）落在 `.runtime/`，不随仓库分发。
- 数据库结构变更通过 Flyway 新版本脚本进行，已应用版本不再修改。
