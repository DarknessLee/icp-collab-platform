# 决策记录

> 一行一条；影响架构/验收的必须写；后续不重复讨论；与 docs/00 同步。
> 待用户确认的决策标注「待确认」，确认后改为「用户确认」。

| 编号 | 日期 | 决策 | 影响/边界 | 来源 |
| --- | --- | --- | --- | --- |
| D01 | 2026-11-03 | 项目根目录 `D:\MyCodes\icp-collab-platform`，工程名/远端仓库名同名 | 目录名与仓库名一致；与现有 MyCodes 下项目不冲突（icp_ 为方案数据表前缀） | 用户指令 + Lead 建议 |
| D02 | 2026-11-03 | 产品形态：B/S Web、前后端分离、浏览器访问；PC 优先、响应式兼容移动浏览器；不做桌面安装包/独立 App/小程序 | 不建 Electron/安装包；移动端仅适配 | 方案 §二 + 手册要求 |
| D03 | 2026-11-03 | 软件名称`企业内控与协同运营管理平台` V1.0（未发表、独立开发）；名称进单一 meta，UI/文档/截图/脚本统一引用 | 截图与文档版本一致 | 方案标题 |
| D04 | 2026-11-03 | 后端 Java 21 LTS（本机 `D:\DevTools\jdk-21.0.13`，Corretto 21.0.13）+ Spring Boot 3.x，替代方案 Java 17 | 方案写 17，Spring Boot 3.x 同时支持 17/21；用启动脚本注入 JAVA_HOME | 环境验证 + 方案 §三（用户确认 Q1） |
| D05 | 2026-11-03 | 数据库 MySQL 8.0.46 项目内独立实例 `127.0.0.1:3307`（自定义 my.ini，数据目录 `.runtime/mysql`，M1 建库 `icp_platform` 与账号 `icp_app` 并改强密码）；不改动现有 MySQL80(3306) | 演示可复现、不依赖现有 root 密码；启停由 scripts 管理 | 已实测（初始化/启动/连接通过）；用户确认 Q5 采用 3307 |
| D06 | 2026-11-03 | 缓存/会话：Caffeine 进程内缓存 + JWT 无状态 + 内存验证码；`CacheService` 接口预留 Redis | 不做 Redis 实装；多实例部署时需换 Redis | 环境无 Redis |
| D07 | 2026-11-03 | 附件/证据：本地磁盘 `.runtime/files` + `FileStorage` 接口（S3/MinIO 可切换），预签名接口保留 | 不做 MinIO 实装；删除/备份需覆盖文件目录 | 环境无 MinIO |
| D08 | 2026-11-03 | Docker Compose / Helm：Compose 作为交付物提供但不本机实测；K8s/Helm 仅预留目录与说明（二期） | 验收场景「一键部署 30 分钟」以本地一键脚本等效验证并在报告标注 | 环境无 Docker |
| D09 | 2026-11-03 | 流程引擎：真实 Flowable 7 + 6 条内置 BPMN（制度发布/缺陷整改/评价计划/文档借阅/采购申请/用印申请）；bpmn-js 仅只读查看，在线设计器暂缓 | ACT_* 表随 Flyway/Flowable 初始化；审批历史用 Flowable History | 方案 §三（用户确认 Q2：采用真实 Flowable 7） |
| D10 | 2026-11-03 | 一期范围：方案 §1.2 七项全做；二期（合同/采购/费用插件、低代码表单、ES、移动端、K8s Helm、多租户）全部不做 | 不做清单见 docs/00 §6 | 方案 §1.2（用户确认 Q4） |
| D11 | 2026-11-03 | 阶段 M0–M4 交付；M5（60 页源码+60 页手册+申请表）可选 | 每阶段门禁与验收命令见 docs/01；用户确认 Q3：一期不做 M5，仅 Demo + 文档，后续如需另行追加 | 手册 + 用户确认 |
| D12 | 2026-11-03 | 端口/路径：后端 8080、前端 dev 5173、前端预览 4173、MySQL 3307、附件 `.runtime/files`、日志 `.runtime/logs` | 并行开发不得抢端口 | Lead 约定 |
| D13 | 2026-11-03 | 构建工具：前端 npm（本机无 pnpm）；后端 Maven（M1 安装便携版 3.9.x 至 D:\DevTools，锁定版本） | 脚本统一以 npm/mvn 为入口 | 环境 |
| D14 | 2026-11-03 | 权限：RBAC + 数据权限（本人/本部门/本部门及下级）；前端隐藏 + 后端注解兜底；每角色至少 1 允许 + 1 拒绝用例 | 自定义部门数据权限暂缓（二期） | 方案 §四/§八 |
| D15 | 2026-11-03 | 种子数据：虚构但合规自洽（企业/人员/编号/金额/日期），规模见 docs/01 §1；`db-validate` 不通过则初始化失败 | Mock 专业、可重复初始化 | 手册原则 6 |
| D16 | 2026-11-03 | 验收命令链：`scripts/db-reset / build / test-api / test-e2e / screenshots / verify`；M1 冻结 | 变更需记录 DECISIONS | Lead 约定 |
| D17 | 2026-11-03 | M0 仅创建根目录与 M0 文档，不写业务代码；先建目录是用户明确要求（便于创建同名远端仓库） | 不违反「M0 确认前不写代码」 | 用户指令 + 手册 M0 |
| D18 | 2026-11-03 | 远端仓库 `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（Gitea 私有，账号 `liyunqing`，凭据由 Windows 凭据管理器保存）；本地 `master` 已推送并跟踪 `origin/master` | 每次阶段结束提交并推送；凭据不写入仓库 | 用户提供（Q5） |
| D19 | 2026-11-03 | 后端采用**单 Spring Boot 模块 + 包分层**（`com.icp.common/system/icp/collab/workflow/report`），不拆 Maven 多模块 | 方案工程结构为多模块；Demo 以包边界等价模块边界，换取构建速度与交付简洁；包依赖规则写入 docs/03 | Lead 建议（M1） |
| D20 | 2026-11-03 | 权限模型落地约定：`sys_role.role_code` 用大写（`ADMIN`/`ICP_MANAGER`/`DEPT_MANAGER`/`EMPLOYEE`/`AUDITOR`/`MANAGEMENT`）；`data_scope` 取值 **1 本人 / 2 本部门 / 3 本部门及下级 / 4 全部 / 5 自定义（二期）**；登录返回 `menus` 为**嵌套树**（M 目录→C 菜单→F 按钮，children 挂载，已授权节点必带祖先目录） | 前端按大写角色码归一化显示；种子与 docs/04/docs/05 一致；后端菜单查询不返回根级叶子 | M1 联调实测（task-3） |
| D21 | 2026-11-03 | `admin` 只挂 `ADMIN` 角色（不再同时挂 `MANAGEMENT`）；角色权限按 docs/05 矩阵单角色定义，避免多角色并集越权 | 避免 admin 因并集获得流程监控/采购/用印等矩阵外权限；exec 由独立账号承担 | M1 联调（task-3） |
| D22 | 2026-11-03 | `sys_menu` 60→88（6 目录 + 39 C + 43 F）、`sys_role_menu` 239→264、`sys_user_role` 30；种子由 `.runtime/tools/gen_seed.py` 生成 V2/V3（生成器不入库），V15 行数断言同步 | docs/01、docs/04 §6.3 已同步；M2 新增菜单/按钮时须更新 V15 与 docs/04 | M1 task-3 |
| D23 | 2026-11-03 | `db-validate` V15 对**运行期增长表**（`sys_oper_log`/`sys_login_log`/`sys_file`）用「不少于种子数」判定，其余表精确相等 | 日志随运行增长，验收脚本可在任意时点复跑；种子重建后仍精确 | M1 验收复跑发现（Lead） |
| D24 | 2026-11-03 | 流程运行数据（实例/待办/历史）由 M2c `scripts/seed-flow.ps1` 调 API 生成，不写 Flyway 种子；M1 仅校验 `wf_process_meta`/`wf_form` 静态种子 | ACT_* 由 Flowable 自建，直接插表不可控 | docs/01、docs/04 §6.3 |
| D25 | 2026-11-03 | 在线会话（`GET /auth/online`、`DELETE /auth/online/{jti}`）用进程内注册表实现，权限复用 `system:user:list`/`system:user:reset`，不新增菜单与种子行 | Redis 替代方案的自然延伸；重启后会话清空属预期；M2a 契约已登记 | M2a 设计（Lead） |
| D26 | 2026-11-03 | 数据变更审计不新增表：复用 `sys_oper_log`，以 `action=CHANGE`、`bizType=表名`、`bizId=主键` 记录脱敏后的 `{before,after}` JSON | 避免 M2a 改 schema 与种子；`GET /logs/changes` 即按此查询；字段长度按 oper_log 现有列截断 | M2a 设计（Lead） |
| D27 | 2026-11-03 | 内控域导出不做后端接口，由前端 `exportCsv.ts` 用列表接口生成 CSV（UTF-8 BOM，pageSize 上限 500）；后端仍校验数据权限与按钮权限 | 减少一套后端导出端点与文件流测试；软著演示足够；M3 全量验收如需要再补 | M2b 设计（Lead） |
| D28 | 2026-11-03 | M2b 阶段制度/评价计划的 `submit` 只做状态流转并返回 `pendingWorkflow=true`，`flow_instance_id` 置空；M2c 接入 Flowable 后改为真实发起流程 | 保证 M2b 可独立验收、前端可先按状态机开发；过渡偏差记入 docs/07-M2b，M2c 必须消解 | M2b 设计（Lead） |
| D29 | 2026-11-03 | 内控数据权限映射列：制度/风险/控制/评价计划 `owner_dept_id`，评价任务 `evaluate_dept_id`（本人维度 `evaluator_id`），缺陷/整改 `dept_id`；本人维度用 `owner_user_id`/`evaluator_id`/`responsible_user_id` | 复用 M2a `@DataScope` 通用列名配置；M2c/M2d 的表按此模式登记映射 | M2b 设计（Lead） |

## 备注

- 状态取值统一使用：已实现 / 部分 / 暂缓 / 不做（见 STATUS/PLAN-MATRIX）。
- 本文件只在有「影响全局边界」的决策时追加；阶段内小决策写进对应验收报告偏差段。

