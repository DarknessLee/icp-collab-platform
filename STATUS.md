# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M2a 组织权限与审计（进行中）；M0、M1 已闭环
- **最新 commit**：`1c9570a`（M1 阶段提交，已推送 origin/master）；M0 收尾提交 `0e9c17b` 系列
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 已推送）
- **M1 验收结论**：通过（详见 `docs/06-M1-验收报告.md`）
  - 契约三件套冻结：`docs/03` 接口、`docs/04` 数据（42 表）、`docs/05` 权限矩阵
  - `db-reset` ×2 幂等；`db-validate` 15/15 PASS；`mvn clean verify` BUILD SUCCESS（3 测试）；`npm run build` exit 0
  - `/actuator/health` = UP；6 角色真实登录，菜单/权限符合 docs/05；前端真实后端回归 47/47 PASS
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`；前端 dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M2）**：
  1. M2a 组织权限与审计：部门/岗位/用户/角色/菜单/字典/参数 CRUD，数据权限拦截器，失败锁定、在线用户，操作/登录/变更审计查询
  2. M2b 内控域：制度/风险/控制矩阵/评价/底稿/缺陷/整改全链路接口与页面
  3. M2c 流程协同：Flowable 7 集成 + 6 条 BPMN、待办/已办/催办/监控/历史、`seed-flow.ps1`
  4. M2d 协同运营：任务/文档/公告消息(SSE)/日程
  5. M2e 看板与预警：覆盖率/风险分布/整改率/流程耗时/预警、定时任务
- **下一步（1~3 条）**：
  1. 冻结 M1 提交并推送（本轮）
  2. 启动 M2a：组织权限 CRUD + 数据权限拦截器 + 审计查询，保持 6 角色允许/拒绝回归
  3. M2a 验收后进入 M2b（内控域），按 `docs/01` 批次门禁执行
- **已知限制**：工作台统计/图表暂为静态演示数据（M2e 接真实接口）；Redis/MinIO 以 Caffeine+本地存储替代（接口已留）；Docker Compose 仅交付文件不本机实测
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M2：docs/01 §2、docs/03、docs/04、docs/05）
- **更新时间**：2026-11-03
