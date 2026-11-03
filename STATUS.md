# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M2b 内控域（进行中）；M0、M1、M2a 已闭环
- **最新 commit**：`7f11af8`（M2a 阶段提交，已推送 origin/master）；M1 提交 `1c9570a` + `6325b7a`
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 已推送）
- **M2a 验收结论**：通过（详见 `docs/07-M2a-验收报告.md`）
  - 组织权限 9 组接口 + 数据权限（1/2/3/4，5 暂缓）+ 登录安全（5 次失败锁 15 分钟、密码策略）+ 在线会话 + 操作/登录/变更审计 + 附件
  - `db-reset` → `db-validate` 15/15 PASS；`mvn clean verify` 48 测试全绿；`scripts/test-api-m2a.ps1` 87/87；Lead 独立验收 `verify-m2a.ps1` 57/57 × 3 次一致
  - 前端系统管理 9 页 + 个人中心 + FileUpload：真实后端 UI 回归 84/84 + 48/48 + 8/8 + 10/10 + 7/7，截图 57 张（`.smoke-frontend\shots\m2a\`）
  - 种子：`sys_menu 105`（6 目录 + 40 C + 59 F）/ `sys_role_menu 281` / `sys_user_role 30`
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`；前端 dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M2）**：
  1. M2b 内控域：制度/风险/控制矩阵/评价与底稿/缺陷/整改全链路接口与页面
  2. M2c 流程协同：Flowable 7 集成 + 6 条 BPMN、待办/已办/催办/监控/历史、`seed-flow.ps1`
  3. M2d 协同运营：任务/文档/公告消息(SSE)/日程
  4. M2e 看板与预警：覆盖率/风险分布/整改率/流程耗时/预警、定时任务
- **下一步（1~3 条）**：
  1. 启动 M2b：制度库/风险库/控制矩阵三张主表 CRUD 与闭环流转（先契约与表结构复用，再前后端并行）
  2. M2b 验收：API 用例 + 前端页面真实回归 + 数据权限（dept01 本部门 / staff01 本人）复验
  3. 通过后进入 M2c（Flowable 单流程 spike → 6 条 BPMN），按 `docs/01` 批次门禁执行
- **已知限制**：工作台统计/图表暂为静态演示数据（M2e 接真实接口）；Redis/MinIO 以 Caffeine+本地存储替代（接口已留）；Docker Compose 仅交付文件不本机实测；自定义数据权限（5）暂缓
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M2b：docs/01 §2、docs/03 §4、docs/04 §3、docs/05）
- **更新时间**：2026-11-03
