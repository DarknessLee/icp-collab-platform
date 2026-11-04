# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M4 交付（进行中）；M0、M1、M2a、M2b、M2c、M2d、M2e、M3 已闭环
- **最新 commit**：M3 提交 `M3_COMMIT`（feat(m3)：Playwright E2E、28 张最终截图、验收脚本六件套、CORS/审计/隐藏菜单/详情名称化/种子文案修复）；M2e `c4668ed` + `088415f`；M2d `f57c831` + `804047a`；M2c `e334734` + `fc97f14`；M2b `fa32065` + `34683e1`；M2a `7f11af8` + `3531d5c`；M1 `1c9570a` + `6325b7a`
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 跟踪 origin/master）
- **M3 验收结论**：通过（详见 `docs/12-M3-验收报告.md`）
  - 证据：`build.ps1` 38s（后端 **170 tests** 0 失败、jar 53.3MB；前端 dist **118 files / 2947.3KB**）；`test-api.ps1` **151.4s**（m2a 87 / m2b 47 / m2d 109 / m2c 70 / m2e 62，全部 0 失败）；`test-e2e.ps1` **23 passed / 1 skipped / 0 failed**（47.2s，0 非预期 console/pageerror）；`screenshots.ps1` 校验 **28 PNG ≥ 25** PASS；`verify.ps1` 全链路 **317.5s PASS**
  - 最终截图：`docs/screenshots/` **S01–S28（28 张 1440×900）** + `INDEX.md`，取自最终演示态（60 实例 / 40 待办 / 20 催办、制度 52）
  - 最终演示态：`demo-reset.ps1` 44s，health UP（JVM pid 45316），monitor 60/40/20、icpadmin todo 40、`sys_menu 179` / `sys_role_menu 540` / `sys_config 16`、policies 52 / risks 40 / defects 30 / tasks 80、`db-validate` 15/15
  - 本轮修复 8 项：CORS 仅放行 5173（4173 下 POST 403）、审计日志筛选参数（keyword→operName/username）、隐藏菜单泄漏（visible=0 过滤+单测）、待办详情裸露 ID（名称化）、种子测试文案（seed-flow + V3 共 31+ 处）、`sys_file.file_path` 与磁盘不一致导致下载 40400、`verify.ps1` fail-fast 失效、截图横向滚动残留
- **M2e 验收结论**：通过（详见 `docs/11-M2e-验收报告.md`）：工作台 summary/trend + 报表 6 端点 + 预警 4 类 + 5 个定时 handler；后端 169 测试、test-api-m2e 62/0、Lead 46/46 ×2、前端 126/126 + 47/47、截图 43 张
- **M2d 验收结论**：通过（详见 `docs/10-M2d-验收报告.md`）：协同 6 组接口（任务/文档/借阅/公告/消息 SSE/日程）；后端 147 测试、API 109/109、Lead 44/44、前端 276/276；修复系统消息 AND 判定缺陷
- **M2c 验收结论**：通过（详见 `docs/09-M2c-验收报告.md`）：Flowable 7.1.0 内嵌 + 6 条 BPMN；待办/已办/我发起/催办/监控/历史/只读高亮；三个 submit 真实发起流程（D28 消除）
- **M2b 验收结论**：通过（详见 `docs/08-M2b-验收报告.md`）：制度→风险→控制矩阵→评价/任务/底稿→缺陷→整改复核关闭全链路；后端 96 测试、API 47/47、Lead 43/43 ×2、前端 87/18/52
- **M2a 验收结论**：通过（详见 `docs/07-M2a-验收报告.md`）：组织权限 9 组接口 + 数据权限 + 登录安全/在线会话 + 审计 + 附件 + 个人中心；API 87/87、Lead 57/57 ×3
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`（Spring Boot 3.3.4 / Java 21 / Flowable 7.1.0，ACT_* 39 张）；前端预览 `http://127.0.0.1:4173`、dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M4）**：
  1. `deploy/`：docker-compose.yml、.env.example、nginx.conf、mysql/init、deploy.sh、backup.sh（Compose 只交付文件、本机无 Docker 不实测）
  2. 本机一键启动与冷启动演练：`scripts/start-local.ps1`、README 冷启动路径（全新 clone → 起库 → 构建 → 启动 → 登录）
  3. 备份/恢复脚本与一次恢复演练（mysqldump + 文件目录，恢复后 db-validate 与关键页面复核）
  4. 文档收敛：`docs/13-部署运维手册`、`docs/14-用户手册`、`docs/15-软著材料清单（一期暂缓说明）`；`scripts/check-docs.ps1`（链接/编号/状态一致性）
  5. README 重写为交付版；M4 提交与推送；最终 `demo-reset` 留演示态
- **下一步（1~3 条）**：
  1. 分派 deploy/ 与 start-local/备份恢复（backend-eng）、docs/13–14 手册与 README（Lead 主笔、frontend-eng 补前端操作与截图）
  2. `check-docs.ps1` + `verify.ps1` 接 check-docs（去掉 SKIP），跑 M4 全链路
  3. 写 M4 验收报告，M4 提交推送并回填 hash
- **已知限制**：Redis/MinIO 以 Caffeine+本地存储替代；Docker Compose 仅交付文件不本机实测；自定义数据权限（5）与多租户暂缓；流程仅一级审批（无加签/转办/在线设计器，含「发起」节点需发起人确认，docs/10 §7.1）；矩阵唯一键与逻辑删除冲突（docs/08 §8）；文档可见性非 ADMIN 仅创建人/授权/借阅；SSE 单实例内存注册；定时器不解析 cronExpression、lastRunTime 为进程内状态；报表实时聚合；审计日志哈希链属等保增强，一期不做
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M4：docs/01 §4、docs/12、README 部署/启动章节）
- **更新时间**：2026-11-04
