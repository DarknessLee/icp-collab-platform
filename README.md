# 企业内控与协同运营管理平台（icp-collab-platform）

> 覆盖「制度 → 风险 → 控制矩阵 → 内控评价 → 缺陷整改」闭环，融合审批流、协同任务、文档与运营看板的企业内控一体化平台。

- **当前阶段**：**M4 交付（已闭环，M0–M4 全部完成）**，一期范围交付完毕（M5 软著材料按 D11 暂缓，见 [STATUS.md](STATUS.md)）
- **版本**：V1.0（未发表）
- **产品形态**：B/S Web，前后端分离，浏览器访问（PC 优先，响应式兼容移动浏览器）
- **远端仓库**：`http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（Gitea 私有，master）

## 技术栈

| 层 | 选型（实际版本） |
| --- | --- |
| 前端 | Vue 3.5.44 + TypeScript 5.7 + Vite 6.4.5 + Element Plus 2.14.7 + Pinia 2.3 + Vue Router 4.6 + axios 1.21 + ECharts 5.6；bpmn-js 17.11.1 只读流程图 |
| 后端 | Java 21 LTS（Corretto 21.0.13）+ Spring Boot 3.3.4 + Spring Security + JWT（jjwt）+ MyBatis-Plus + Flyway；Flowable 7.1.0（6 条内置 BPMN） |
| 数据库 | MySQL 8.0.46（项目内独立实例 `127.0.0.1:3307`，库 `icp_platform`）+ Flyway V1/V2/V3（42 张业务表 + ACT_* 39 张） |
| 存储/缓存 | 本地磁盘附件（`FileStorage` 接口，预留 S3/MinIO）／进程内 Caffeine（`CacheService` 接口，预留 Redis） |
| 测试 | JUnit 5 + Spring Boot Test（170 tests）；Playwright E2E（`e2e/`，E01–E14 + 截图 spec）；API 验收脚本 5 套（375 例） |
| 部署 | Docker Compose 文件（M4 交付，本机无 Docker 不实测）+ 本地 PowerShell 脚本（一键启动/重置/备份） |

## 功能一览（全部已实现并验收）

- **组织权限与系统管理**：部门/岗位/用户/角色/菜单/字典/参数/定时任务 9 页 + 个人中心；RBAC + 数据权限（本人/本部门/本部门及下级）；登录安全（JWT、失败锁定、在线会话/强制下线）、操作/登录/变更审计、附件中心。
- **内控业务**：制度库（分类/版本/发布审批）、风险库（概率×影响评分定级）、控制点、控制矩阵（覆盖率）、评价计划→任务→底稿、缺陷登记→整改→复核关闭。
- **流程中心**：Flowable 7 内嵌 + 6 条流程（制度发布、缺陷整改、评价计划、文档借阅、采购申请、用印申请）；待办/已办/我发起、同意/驳回/重提/催办、流程监控与超时、只读 BPMN 高亮、审批历史留痕。
- **协同运营**：任务中心（子任务/评论/提醒）、文档中心（目录/权限/版本/下载）、文档借阅、公告（范围/已读）、站内信（SSE 实时）、日程月历。
- **报表与预警**：工作台 8 卡 + 趋势、内控概览/制度/缺陷/任务/预警 5 个报表页、4 类预警与 5 个定时扫描任务。
- **演示态**：60 个流程实例（40 待办 / 20 已完成 / 20 催办）、52 条制度、40 条风险、30 条缺陷、80 条协同任务；截图见 [`docs/screenshots/`](docs/screenshots/)。

## 界面预览

> 截图取自演示态（`scripts/demo-reset.ps1` 之后：60 实例 / 40 待办 / 20 催办，制度 52 / 风险 40 / 缺陷 30），前端为 4173 预览构建，登录账号 `admin`。完整 28 张与逐张说明见 [`docs/screenshots/INDEX.md`](docs/screenshots/INDEX.md)，重新采集用 `scripts\screenshots.ps1 -Take`。

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

| 整改复核（缺陷关闭留痕） |
| --- |
| ![整改复核](docs/screenshots/s09-rectify.png) |

### 流程中心（Flowable 7，6 条内置流程）

| 待办中心（40 待办） | 待办详情（表单 + 审批历史 + BPMN 高亮） |
| --- | --- |
| ![待办](docs/screenshots/s10-todo.png) | ![待办详情](docs/screenshots/s27-todo-detail.png) |

| 流程监控（运行/超时/催办） | 采购申请 |
| --- | --- |
| ![流程监控](docs/screenshots/s11-monitor.png) | ![采购申请](docs/screenshots/s12-purchase.png) |

| 用印申请 |
| --- |
| ![用印申请](docs/screenshots/s13-seal.png) |

### 协同运营（任务 / 文档 / 公告 / 消息 / 日程）

| 任务中心（子任务/评论/提醒） | 文档中心（目录/权限/版本） |
| --- | --- |
| ![任务中心](docs/screenshots/s15-collab-task.png) | ![文档中心](docs/screenshots/s16-doc-center.png) |

| 文档借阅审批归还 | 公告通知（范围/已读） |
| --- | --- |
| ![文档借阅](docs/screenshots/s14-borrow.png) | ![公告](docs/screenshots/s17-notice.png) |

| 消息中心（SSE 实时推送） | 日程月历 |
| --- | --- |
| ![消息中心](docs/screenshots/s18-message.png) | ![日程月历](docs/screenshots/s19-schedule.png) |

### 报表与预警

| 内控概览报表 | 缺陷统计（严重程度×状态） |
| --- | --- |
| ![内控概览](docs/screenshots/s03-report-overview.png) | ![缺陷统计](docs/screenshots/s20-report-defect.png) |

| 预警中心（4 类规则 + 5 个定时扫描任务） |
| --- |
| ![预警中心](docs/screenshots/s21-alert.png) |

### 系统管理与权限（RBAC + 数据权限 + 审计）

| 角色菜单授权（540 条角色菜单） | 审计操作日志（含 traceId 追踪） |
| --- | --- |
| ![角色授权](docs/screenshots/s23-role-menu.png) | ![审计日志](docs/screenshots/s22-audit-log.png) |

| 个人中心 | 越权访问 403（6 角色对比） |
| --- | --- |
| ![个人中心](docs/screenshots/s24-profile.png) | ![403](docs/screenshots/s25-forbidden-403.png) |

### 移动端适配

| 窄屏侧栏抽屉（响应式，≤768px） |
| --- |
| ![移动端](docs/screenshots/s28-mobile-sidebar.png) |

## 快速开始（本机已验证）

前置：JDK 21（`D:\DevTools\jdk-21.0.13`）、Maven 3.9.9（`D:\DevTools\apache-maven-3.9.9`）、Node 24、本机 MySQL 8 安装（仅用其 mysqld 二进制，不启动 3306 服务）。

```powershell
cd D:\MyCodes\icp-collab-platform
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1       # 一键：MySQL 3307 + 后端 8080 + 前端预览 4173（冷启动约 46s，重跑幂等）
powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1        # 可选：重置到演示态（60/40/20，约 40–45s）
```

- 访问 `http://127.0.0.1:4173`（一键启动即打开预览；开发模式用 `scripts\dev-frontend.ps1` → 5173），演示账号：`admin`（系统管理员）、`icpadmin`（内控管理员）、`auditor`（审计员）、`exec`（管理层）、`dept01`–`dept06`（部门负责人）、`staff01`–`staff20`（普通员工），密码统一 `Icp@2026`。
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`；Swagger：`/swagger-ui/index.html`。
- 停止：`scripts\stop-local.ps1`（加 `-WithDb` 连 MySQL 一起停）。
- 备份/恢复：`scripts\backup.ps1` → `scripts\restore.ps1 -Path <备份目录>`（实测 1.1s / 16.7s，附件与数据完整回滚）。
- Docker 部署：见 [`deploy/README.md`](deploy/README.md) 与 [docs/13 部署与运维手册](docs/13-部署与运维手册.md)（Compose 为交付件，本机无 Docker 未实测）。

## 验收命令（可串行复跑）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build.ps1        # 后端 170 tests + 前端构建（约 38s）
powershell -ExecutionPolicy Bypass -File scripts\test-api.ps1 -Reset   # 全量 API 375 例（约 152s）
powershell -ExecutionPolicy Bypass -File scripts\test-e2e.ps1     # Playwright E2E 23 passed（约 47s）
powershell -ExecutionPolicy Bypass -File scripts\screenshots.ps1 -Take  # 重新采集 28 张截图（-Take 才采集）
powershell -ExecutionPolicy Bypass -File scripts\check-docs.ps1   # 文档一致性（PASS=16）
powershell -ExecutionPolicy Bypass -File scripts\verify.ps1       # 一键全链路（约 248–318s，PASS）
```

> 运行顺序约束：`m2a/m2b` 必须在 `db-reset` 后、`seed-flow` 之前（m2b 有精确计数断言）；`m2e` 需单独 clean seed；E2E 与截图必须在 `demo-reset` 之后。

## 目录结构

```text
icp-collab-platform/
├─ backend/                 # Spring Boot 3.3.4 单模块 com.icp：common / system / icp / workflow / collab / report
│  └─ src/main/resources/   # db/migration（V1 schema 42 表 / V2 seed_base / V3 seed_business）+ processes/（6 条 BPMN）
├─ frontend/                # Vue3 + Vite：登录、主框架、工作台、系统管理 9 页、内控 7 页、流程中心 9 页、协同 6 页、报表 5 页
├─ e2e/                     # Playwright：E01–E14 用例 + screenshots.spec.ts + fixtures
├─ scripts/                 # start-local/stop-local、backup/restore、demo-reset、build、test-api(-m2a…m2e)、test-e2e、
│                           # screenshots、seed-flow、check-docs、verify
├─ docs/                    # 00 画像、01 阶段计划、02 差距风险、03 契约、04 数据模型与种子、05 权限矩阵、06–16 验收报告、
│                           # 13 部署运维、14 用户手册、15 软著清单、screenshots/
├─ .runtime/                # 本地 MySQL 数据、附件、日志（gitignore）
├─ deploy/                  # Docker Compose、Nginx、初始化与备份脚本（M4 交付）
├─ STATUS.md / DECISIONS.md / PLAN-MATRIX.md
└─ README.md                # 交付唯一入口
```

## 文档入口

1. [STATUS.md](STATUS.md) —— 当前阶段、最近验收、下一步
2. [DECISIONS.md](DECISIONS.md) —— 决策日志（D01–D43）
3. [PLAN-MATRIX.md](PLAN-MATRIX.md) —— 方案要求 vs 当前实现/证据/状态
4. [docs/00-项目画像.md](docs/00-项目画像.md) —— 画像、范围、角色、不做清单
5. [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md) —— 阶段门禁、E2E 用例、截图清单与验收命令
6. [docs/02-方案差距表与风险.md](docs/02-方案差距表与风险.md) —— 差距、假设、风险、已确认问题
7. [docs/03-接口契约.md](docs/03-接口契约.md)、[docs/04-数据模型与种子.md](docs/04-数据模型与种子.md)、[docs/05-权限矩阵.md](docs/05-权限矩阵.md) —— 冻结契约
8. 验收报告：[docs/06-M1](docs/06-M1-验收报告.md)、[docs/07-M2a](docs/07-M2a-验收报告.md)、[docs/08-M2b](docs/08-M2b-验收报告.md)、[docs/09-M2c](docs/09-M2c-验收报告.md)、[docs/10-M2d](docs/10-M2d-验收报告.md)、[docs/11-M2e](docs/11-M2e-验收报告.md)、[docs/12-M3](docs/12-M3-验收报告.md)、[docs/16-M4 交付验收](docs/16-M4-交付验收报告.md)
9. 交付文档：[docs/13 部署与运维](docs/13-部署与运维手册.md)、[docs/14 用户手册](docs/14-用户手册.md)、[docs/15 软著材料清单](docs/15-软著材料清单.md)
10. [docs/screenshots/INDEX.md](docs/screenshots/INDEX.md) —— 28 张最终截图索引

## 交付边界（一期）

- **做**：组织权限、登录与审计、制度/风险/控制矩阵、内控评价、缺陷整改闭环、6 条内置审批流、任务/文档/公告/消息/日程、看板与预警、部署文件与文档、Playwright E2E 与 28 张截图
- **不做（二期）**：多租户实装、真实 Redis/MinIO/ES、OAuth2/LDAP、电子签名、K8s/Helm 实机、监控栈、独立移动端、低代码表单、在线流程设计器、在线文档预览、合同/采购/费用插件
- 软著材料（M5）：用户确认一期不做（DECISIONS D11）

## 约定

- 私有《软著 Demo 手册》不进入本仓库、不上传远端；仓库内只放项目自身文档与代码。
- 开发期验收脚本、调试日志与过程截图统一归档在 `icp-collab-platform\.runtime\artifacts\`（`.runtime/` 已 gitignore，不随仓库分发，也不在上级工作目录残留）；交付用截图只在 `docs/screenshots/`。
- 阶段门禁、验收命令与汇报格式见 [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md)。
- 源码统一 UTF-8；本机为 Windows PowerShell 5.1（无 pwsh7），仓库内脚本保持 ASCII、避免 PS7 语法；中文文件用编辑器/工具写入（勿用 shell 重定向，避免 BOM/GBK 乱码）。
