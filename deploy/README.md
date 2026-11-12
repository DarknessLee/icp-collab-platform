# 部署与运维（Docker Compose）

> 企业内控与协同运营管理平台 V1.0 · 本目录为**交付部署文件**。
> **重要说明：开发机没有 Docker，本目录未在本机实测**；所有文件按官方镜像与 Compose v2 语法编写，并与
> `backend/src/main/resources/application.yml` 的 `${ENV:默认值}` 占位逐项对齐，供评审/目标机执行。
> 本机（Windows）的等价一键脚本见仓库根 `scripts/start-local.ps1`、`scripts/stop-local.ps1`、
> `scripts/backup.ps1`、`scripts/restore.ps1`（这些均已实测）。

## 1. 目录

```text
deploy/
├─ docker-compose.yml            # 5 个服务：frontend / backend / mysql / redis / minio
├─ .env.example                  # 环境变量样例（复制为 .env 后修改口令）
├─ nginx.conf                    # 前端静态托管 + /api 反代 + gzip + 25m 上传
├─ docker/
│  ├─ backend.Dockerfile         # maven 构建 → JRE 21 运行
│  └─ frontend.Dockerfile        # node 构建 → nginx 托管
├─ mysql/init/
│  ├─ 01-charset.sql             # utf8mb4 + Asia/Shanghai
│  └─ 02-grant.sql               # 应用账号授权（默认 icp_app）
├─ scripts/
│  ├─ deploy.sh                  # 检查 docker → .env → build → up → 等 health
│  ├─ backup.sh                  # mysqldump + 附件卷打包 + manifest.json + 保留 N 份
│  └─ restore.sh                 # 停后端 → 重建库 → 导入 → 还原附件 → 等 health
└─ README.md
```

## 2. 30 分钟部署步骤（目标机 Linux + Docker 24+/Compose v2）

```bash
# 1) 准备
git clone <repo> icp-collab-platform && cd icp-collab-platform/deploy
cp .env.example .env
vi .env                      # 必改：MYSQL_ROOT_PASSWORD / APP_DB_PASSWORD / JWT_SECRET / MINIO_ROOT_PASSWORD

# 2) 一键部署（首次会自动构建前后端镜像，约 5–10 分钟）
bash scripts/deploy.sh

# 3) 验证
docker compose ps            # mysql/backend/frontend 应为 healthy
curl -s http://localhost:8081/actuator/health   # {"status":"UP",...}
# 浏览器打开 http://localhost:8080 ，admin / Icp@2026
```

首次启动时 MySQL 空库 → 后端启动阶段由 **Flyway** 自动执行 `V1__schema.sql` / `V2__seed_base.sql` /
`V3__seed_business.sql`，得到与验收一致的种子数据（含 179 菜单 / 540 角色菜单 / 40 制度等）。
如需 60 实例 + 20 催办的**初始数据态**（本机 `reset-data.ps1` 等价物），在容器内执行：

```bash
docker compose exec backend sh -c 'java -version'   # 确认容器可用
# 初始数据态脚本依赖 PowerShell/本机 MySQL，容器内不提供；示例数据请使用本目录外的
# scripts/reset-data.ps1（Windows 本地）或直接使用种子态（功能完整，只是流程实例较少）。
```

## 3. 端口 / 账号

| 服务 | 容器端口 | 宿主机默认 | 变量 | 说明 |
| --- | --- | --- | --- | --- |
| frontend (Nginx) | 80 | **8080** | `FRONTEND_PORT` | 浏览入口；`/api` 反代到 backend:8080 |
| backend (Spring Boot) | 8080 | **8081** | `BACKEND_PORT` | 直连调试/Swagger；健康检查 `/actuator/health` |
| mysql | 3306 | **13307** | `MYSQL_PORT` | 避开本机既有 3307；库名 `MYSQL_DATABASE` |
| minio | 9000/9001 | 9000/9001 | `MINIO_PORT` / `MINIO_CONSOLE_PORT` | 二期对象存储预留 |
| redis | 6379 | 不映射 | — | 二期缓存预留（当前后端用 Caffeine） |

预置账号：`admin`、`icpadmin`、`auditor`、`exec`、`dept01`–`dept06`、`staff01`–`staff20`，口令统一 `Icp@2026`。
生产部署请立即修改管理员口令，并把 `.env` 排除在版本库外。

## 4. 配置对应关系（Compose ↔ application.yml）

后端的可覆盖项统一在 `backend/src/main/resources/application.yml` 用 `${ENV:默认值}` 声明；本地
`application-local.yml` 仍硬编码 `127.0.0.1:3307`，因此**本机默认行为不变**。对应关系：

| Compose 环境变量 | application.yml 占位 | 默认值（本地） | 容器值 |
| --- | --- | --- | --- |
| `SERVER_PORT` | `server.port` | `8080` | `8080` |
| `DB_URL` | `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3307/icp_platform?...` | `jdbc:mysql://mysql:3306/icp_platform?...` |
| `DB_USERNAME` | `spring.datasource.username` | `icp_app` | `${APP_DB_USER}` |
| `DB_PASSWORD` | `spring.datasource.password` | `Icp@2026` | `${APP_DB_PASSWORD}` |
| `JWT_SECRET` | `icp.jwt.secret` | 本地 development key | `.env` 的 base64 key |
| `STORAGE_ROOT` | `icp.storage.local-root` | `../.runtime/files` | `/app/data/files`（挂 `app-files` 卷） |
| `CORS_ORIGINS` | `app.cors.allowed-origins` | 5173/4173 四个源 | `http://localhost:8080` |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `local` | `container` |

> `SPRING_PROFILES_ACTIVE=container` 时只加载 `application.yml`：数据源/端口/JWT/存储都来自上表
> 环境变量，Flyway 使用默认 `classpath:db/migration` 自动迁移。

## 5. 备份与恢复

```bash
# 备份（数据库 + 附件卷；输出 deploy/backups/<ts>/{db.sql,attachments.tgz,manifest.json}）
bash scripts/backup.sh                 # 默认保留最近 10 份
bash scripts/backup.sh --keep 5

# 恢复（停后端 → DROP/CREATE 库 → 导入 → 还原附件 → 等 health）
bash scripts/restore.sh backups/20261104-120000
curl -s http://localhost:8081/actuator/health
```

`manifest.json` 记录时间、两个包的字节数与 SHA-256、以及 `sys_user` / `icp_policy` / `icp_risk` /
`icp_defect` / `coll_task` / `wf_business_link` 行数，便于恢复后核对。
Windows 本机的等价脚本 `scripts/backup.ps1` / `scripts/restore.ps1` 已完成一次「改坏一条数据 → 恢复 →
db-validate 15/15 + 行数复核」演练（数据见 docs/13 附录）。

## 6. 离线部署

1. 在可联网机器构建并导出镜像：

```bash
docker compose build
docker save -o images/icp-platform-backend-1.0.tar icp-platform-backend:1.0
docker save -o images/icp-platform-frontend-1.0.tar icp-platform-frontend:1.0
docker pull mysql:8.0 redis:7-alpine minio/minio:latest alpine:3.20
docker save -o images/icp-platform-infra.tar mysql:8.0 redis:7-alpine minio/minio:latest alpine:3.20
```

2. 拷贝 `deploy/`（含 `images/` 与 `.env`）到内网目标机，执行：

```bash
bash scripts/deploy.sh --offline
```

`--offline` 会跳过 `build`，改为 `docker load` 全部 `images/*.tar` 后直接 `up -d`。

## 7. 常见问题

- **backend 一直 unhealthy**：`docker compose logs --tail 100 backend`；多为 `.env` 中 DB 口令与
  `MYSQL_ROOT_PASSWORD` 不一致，或 MySQL 首次初始化尚未完成（`start_period` 60s）。
- **登录 403 / CORS**：同源经 Nginx 反代不应触发 CORS；若把前端独立域名部署，改 `.env` 的
  `CORS_ORIGINS` 为实际来源（逗号分隔）。
- **上传大文件失败**：Nginx `client_max_body_size 25m`（`deploy/nginx.conf`），后端上限 50MB
  （`spring.servlet.multipart.max-file-size`），如需调大需两处同时改。
- **端口冲突**：`.env` 改 `FRONTEND_PORT` / `BACKEND_PORT` / `MYSQL_PORT` 后重新 `docker compose up -d`。
- **附件丢失**：附件在 `app-files` 卷（`STORAGE_ROOT=/app/data/files`），换机/迁移请用 `backup.sh`
  的 `attachments.tgz`，不要只拷数据库。
- **初始数据态流程实例**：容器内的种子态没有 60 个流程实例；如需完整示例数据，请在 Windows 本机按
  README「快速开始」运行 `reset-data.ps1`（该路径已验证）。

## 8. 与旧版/本地部署的差异

| 项 | 本机（已验证） | Docker Compose（仅交付） |
| --- | --- | --- |
| 数据库 | 项目内 MySQL 3307（`.runtime/mysql`） | `mysql:8.0` 容器，卷 `mysql-data` |
| 后端 | `mvn spring-boot:run`（8080） | `backend:8080`，宿主 8081 |
| 前端 | `npm run preview`（4173） | Nginx 80，宿主 8080 |
| 附件 | `.runtime/files` | 卷 `app-files` → `/app/data/files` |
| 缓存/对象存储 | Caffeine / 本地磁盘 | redis/minio 容器（预留，暂未接入业务） |
| 备份 | `scripts/backup.ps1`（已实测） | `deploy/scripts/backup.sh`（未实测） |
