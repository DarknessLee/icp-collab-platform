# 企业内控与协同运营管理平台（icp-collab-platform）

> 覆盖「制度 → 风险 → 控制矩阵 → 内控评价 → 缺陷整改」闭环，融合审批流、协同任务、文档与运营看板的企业内控一体化平台。

- **当前阶段**：M2c 流程协同（M0、M1、M2a、M2b 已闭环，见 [STATUS.md](STATUS.md)）
- **版本**：V1.0（未发表）
- **产品形态**：B/S Web，前后端分离，浏览器访问（PC 优先，响应式兼容移动浏览器）
- **远端仓库**：`http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（Gitea 私有，master）

## 技术栈

| 层 | 选型（实际版本） |
| --- | --- |
| 前端 | Vue 3.5.44 + TypeScript 5.7 + Vite 6.4.5 + Element Plus 2.14.7 + Pinia 2.3 + Vue Router 4.6 + axios 1.21 + ECharts 5.6；bpmn-js 只读流程图（M2c） |
| 后端 | Java 21 LTS（Corretto 21.0.13）+ Spring Boot 3.3.4 + Spring Security + JWT（jjwt）+ MyBatis-Plus + Flyway；Flowable 7（M2c 接入） |
| 数据库 | MySQL 8.0.46（项目内独立实例 `127.0.0.1:3307`，库 `icp_platform`）+ Flyway V1/V2/V3 |
| 存储/缓存 | 本地磁盘附件（`FileStorage` 接口，预留 S3/MinIO）／进程内 Caffeine（`CacheService` 接口，预留 Redis） |
| 测试 | JUnit 5 + Spring Boot Test；Playwright（浏览器回归，脚本在仓库外用于 M1 联调） |
| 部署 | Docker Compose 文件（M4 交付，本机无 Docker 不实测）+ 本地 PowerShell 脚本 |

## 快速开始（本机已验证）

前置：JDK 21（`D:\DevTools\jdk-21.0.13`）、Maven 3.9.9（`D:\DevTools\apache-maven-3.9.9`）、Node 24、本机 MySQL 8 安装（仅用其 mysqld 二进制，不启动 3306 服务）。

```powershell
cd D:\MyCodes\icp-collab-platform
powershell -ExecutionPolicy Bypass -File scripts\mysql-init.ps1     # 首次：初始化项目内 3307 实例
powershell -ExecutionPolicy Bypass -File scripts\db-reset.ps1       # 重建库 + Flyway + 种子 + 校验（应 15/15 PASS）
powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1  # 后端 :8080
powershell -ExecutionPolicy Bypass -File scripts\dev-frontend.ps1   # 前端 :5173
```

- 访问 `http://127.0.0.1:5173`，演示账号：`admin`（系统管理员）、`icpadmin`（内控管理员）、`auditor`（审计员）、`exec`（管理层）、`dept01`–`dept06`（部门负责人）、`staff01`–`staff20`（普通员工），密码统一 `Icp@2026`。
- 后端健康检查：`http://127.0.0.1:8080/actuator/health`；Swagger：`/swagger-ui/index.html`。
- 停止：`scripts/stop-backend.ps1`、`scripts/mysql-stop.ps1`。

## 目录结构（M1 现状）

```text
icp-collab-platform/
├─ backend/                 # Spring Boot 3.3.4 单模块 com.icp：common / system / icp（collab / workflow / report 待 M2c–M2e）
│  └─ src/main/resources/db/migration/   # V1 schema(42 表) / V2 seed_base / V3 seed_business
├─ frontend/                # Vue3 + Vite：登录、主框架、动态路由、v-perm、工作台、系统管理 9 页、内控 7 页、403/404
├─ scripts/                 # mysql-init/start/stop、db-reset、db-validate、build-backend、build-frontend、start/stop-backend、dev-frontend、test-api-m2a、test-api-m2b
├─ docs/                    # 00 画像、01 阶段计划、02 差距风险、03 契约、04 数据模型与种子、05 权限矩阵、06–08 阶段验收报告
├─ .runtime/                # 本地 MySQL 数据、附件、日志（gitignore）
├─ deploy/                  # Docker Compose 等（M4 交付）
├─ STATUS.md / DECISIONS.md / PLAN-MATRIX.md
└─ README.md                # 交付唯一入口（M4 收敛）
```

## 文档入口

1. [STATUS.md](STATUS.md) —— 当前阶段、最近验收、下一步
2. [DECISIONS.md](DECISIONS.md) —— 决策日志（D01–D29）
3. [PLAN-MATRIX.md](PLAN-MATRIX.md) —— 方案要求 vs 当前实现/证据/状态
4. [docs/00-项目画像.md](docs/00-项目画像.md) —— 画像、范围、角色、不做清单
5. [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md) —— 阶段门禁与验收命令
6. [docs/02-方案差距表与风险.md](docs/02-方案差距表与风险.md) —— 差距、假设、风险、已确认问题
7. [docs/03-接口契约.md](docs/03-接口契约.md)、[docs/04-数据模型与种子.md](docs/04-数据模型与种子.md)、[docs/05-权限矩阵.md](docs/05-权限矩阵.md) —— M1 冻结契约
8. [docs/06-M1-验收报告.md](docs/06-M1-验收报告.md)、[docs/07-M2a-验收报告.md](docs/07-M2a-验收报告.md)、[docs/08-M2b-验收报告.md](docs/08-M2b-验收报告.md) —— 阶段实际命令、数字、偏差与遗留

## 交付边界（一期）

- **做**：组织权限、登录与审计、制度/风险/控制矩阵、内控评价、缺陷整改闭环、6 条内置审批流、任务/文档/公告/消息、看板与预警、部署文件与文档
- **不做（二期）**：多租户实装、真实 Redis/MinIO/ES、OAuth2/LDAP、电子签名、K8s/Helm 实机、监控栈、独立移动端、低代码表单、在线流程设计器、在线文档预览、合同/采购/费用插件
- 软著材料（M5）：用户确认一期不做（DECISIONS D11）

## 约定

- 私有《软著 Demo 手册》不进入本仓库、不上传远端；仓库内只放项目自身文档与代码。
- 阶段门禁、验收命令与汇报格式见 [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md)。
- 源码统一 UTF-8；本机为 Windows PowerShell 5.1（无 pwsh7），仓库内脚本保持 ASCII、避免 PS7 语法；中文文件用编辑器/工具写入（勿用 shell 重定向，避免 BOM/GBK 乱码）。
