# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M2d 协同运营（进行中）；M0、M1、M2a、M2b、M2c 已闭环
- **最新 commit**：`M2C_COMMIT`（M2c 阶段提交，推送 origin/master 后由 docs 提交回填）；M2b 提交 `fa32065` + `34683e1`；M2a 提交 `7f11af8` + `3531d5c`；M1 提交 `1c9570a` + `6325b7a`
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 已推送）
- **M2c 验收结论**：通过（详见 `docs/09-M2c-验收报告.md`）
  - Flowable 7.1.0 内嵌 + 6 条 BPMN（制度发布/缺陷整改/评价计划/文档借阅/采购/用印）；待办/已办/我发起/催办/监控/历史/bpmn-js 只读高亮全部可用；`POST /policies|evaluations|rectifies/{id}/submit` 已真实发起流程（D28 过渡消除）
  - 后端 `mvn clean verify` 98 测试全绿；`test-api-m2c.ps1` 70/70；Lead 独立 `verify-m2c.ps1` 77/77 × 3；`test-api-m2a` 87/87、`test-api-m2b` 47/47 不回归；`db-validate` 15/15
  - 前端真实回归 69/69 + 55/55 + 28/28 + 21/21；截图 `D:\MyCodes\.smoke-frontend\shots\m2c\`
  - 演示态：60 实例（40 运行中/20 完成）/ 40 待办 / 20 催办；6 角色 todo admin 0 / icpadmin 40 / auditor 0 / exec 17 / dept01 4 / staff01 0
  - 种子：`sys_menu 149`（6 目录 + 41 C + 102 F）/ `sys_role_menu 431` / `sys_config 16`；ACT_* 39 张由引擎自建
  - 6 角色 permissions：admin 86 / icpadmin 107 / auditor 53 / exec 51 / dept01 70 / staff01 33
- **M2b 验收结论**：通过（详见 `docs/08-M2b-验收报告.md`）：制度→风险→控制矩阵→评价/任务/底稿→缺陷→整改复核关闭 全链路；后端 96 测试、API 47/47、Lead 43/43 ×2、前端 87/18/52
- **M2a 验收结论**：通过（详见 `docs/07-M2a-验收报告.md`）：组织权限 9 组接口 + 数据权限（1/2/3/4，5 暂缓）+ 登录安全 + 在线会话 + 审计查询 + 附件 + 个人中心；API 87/87、Lead 57/57 ×3、前端 157/157
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`（Flowable 7.1.0，ACT_* 39 张）；前端 dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M2）**：
  1. M2d 协同运营：任务中心（计划/任务/子任务/优先级/截止提醒）、文档中心（目录/权限/版本/下载）、公告已读、站内信 SSE、日程
  2. M2e 报表看板与预警：覆盖率/风险分布/整改率/流程耗时聚合、超时任务/到期制度/高风险缺陷预警、定时任务
- **下一步（1~3 条）**：
  1. 启动 M2d：先冻结 docs/03 §6 协同接口与 docs/04 协同表口径，再分派后端/前端任务
  2. 文档中心与流程中心借阅联动（C22 遗留：借阅通过后下载权限生效）
  3. M2d 验收：API 用例 + 真实回归 + 6 角色权限 + 截图 + docs/10-M2d 验收报告
- **已知限制**：工作台统计/图表仍部分静态（M2e 接真实接口）；Redis/MinIO 以 Caffeine+本地存储替代；Docker Compose 仅交付文件不本机实测；自定义数据权限（5）暂缓；流程仅一级审批（无加签/转办/在线设计器）；矩阵唯一键与逻辑删除冲突（docs/08 §8）；seed-flow 后业务单据数高于种子基线（采购 34/用印 22/借阅 18）
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M2d：docs/01 §4、docs/03 §6、docs/04 §5、docs/05）
- **更新时间**：2026-11-03
