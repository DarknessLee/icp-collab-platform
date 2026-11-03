# 企业内控与协同运营管理平台（icp-collab-platform）

> 覆盖「制度 → 风险 → 控制矩阵 → 内控评价 → 缺陷整改」闭环，融合审批流、协同任务、文档与运营看板的企业内控一体化平台。

- **当前阶段**：M0 立项（仅文档与目录，尚未写业务代码）
- **版本**：V1.0（未发表）
- **产品形态**：B/S Web，前后端分离，浏览器访问（PC 优先，响应式兼容移动浏览器）
- **远端仓库**：待创建（同名私有仓库），地址到位后配置 remote

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + ECharts + bpmn-js（只读流程图） |
| 后端 | Java 21 LTS + Spring Boot 3.x + Spring Security + JWT + MyBatis-Plus + Flowable 7 |
| 数据库 | MySQL 8.0.46（项目内独立实例 127.0.0.1:3307）+ Flyway |
| 存储/缓存 | 本地磁盘附件（FileStorage 接口预留 S3）／进程内 Caffeine（CacheService 接口预留 Redis） |
| 测试 | JUnit 5 + Spring Boot Test + Playwright（npm） |
| 部署 | Docker Compose 文件（交付，未本机实测）+ 本地一键 PowerShell 脚本 |

## 目录规划（M1 起逐步落地）

```text
icp-collab-platform/
├─ backend/                 # Spring Boot 多模块：common / system / icp / collab / workflow / report
├─ frontend/                # Vue3 + Vite
├─ deploy/                  # docker-compose.yml、nginx.conf、.env.example、scripts/deploy.sh、backup.sh
├─ scripts/                 # db-reset / build / test-api / test-e2e / screenshots / verify（PowerShell）
├─ docs/                    # 00 项目画像、01 阶段计划、02 差距表、03 契约、04 数据、05 权限、截图、报告
├─ .runtime/                # 本地 MySQL 数据目录、附件、日志（gitignore）
├─ STATUS.md                # 当前状态（每阶段更新）
├─ DECISIONS.md             # 决策日志
├─ PLAN-MATRIX.md           # 方案-实现追踪矩阵
└─ README.md                # 交付唯一入口（M4 收敛完成）
```

## 文档入口

1. [STATUS.md](STATUS.md) —— 当前阶段、最近验收、下一步
2. [DECISIONS.md](DECISIONS.md) —— 已确认决策（避免重复讨论）
3. [PLAN-MATRIX.md](PLAN-MATRIX.md) —— 方案要求 vs 实现状态
4. [docs/00-项目画像.md](docs/00-项目画像.md) —— 画像、范围、角色、不做清单
5. [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md) —— 阶段门禁与验收命令
6. [docs/02-方案差距表与风险.md](docs/02-方案差距表与风险.md) —— 差距、假设、风险、待确认问题

## 启动（M1 完成后补全）

M0 阶段无可运行代码。M1 完成后提供：

```powershell
scripts/start-local.ps1   # 启动项目内 MySQL 实例（3307）+ 后端（8080）+ 前端预览
```

## 交付边界（一期）

- **做**：组织权限、登录与审计、制度/风险/控制矩阵、内控评价、缺陷整改闭环、6 条内置审批流、任务/文档/公告/消息、看板与预警、部署文件与文档
- **不做（二期）**：多租户实装、真实 Redis/MinIO/ES、OAuth2/LDAP、电子签名、K8s/Helm 实机、监控栈、独立移动端、低代码表单、在线流程设计器、在线文档预览、合同/采购/费用插件、软著材料（待确认）

## 约定

- 私有《软著 Demo 手册》不进入本仓库、不上传远端；仓库内只放项目自身文档与代码。
- 阶段门禁、验收命令与汇报格式见 [docs/01-阶段计划与验收口径.md](docs/01-阶段计划与验收口径.md)。
- 源码统一 UTF-8；Windows + PowerShell 7 环境。
