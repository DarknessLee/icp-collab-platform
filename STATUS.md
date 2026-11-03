# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M2c 流程协同（进行中）；M0、M1、M2a、M2b 已闭环
- **最新 commit**：`M2B_COMMIT`（M2b 阶段提交，已推送 origin/master）；M2a 提交 `7f11af8` + `3531d5c`；M1 提交 `1c9570a` + `6325b7a`
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 已推送）
- **M2b 验收结论**：通过（详见 `docs/08-M2b-验收报告.md`）
  - 制度→风险→控制矩阵→评价计划/任务/底稿→缺陷→整改复核关闭 全链路前后端可用；编码/状态机/评分定级/覆盖率/start 幂等/数据权限均生效
  - 后端 `mvn clean verify` 96 测试全绿；`scripts/test-api-m2b.ps1` 47/47；Lead 独立验收 `verify-m2b.ps1` 43/43 × 2 次一致；`db-validate` 15/15 PASS
  - 前端内控 7 页 + CSV 导出：真实后端 UI 回归 87/87 + 18/18 + 52/52；截图 60+ 张（`.smoke-frontend\shots\m2b\`）
  - 种子：`sys_menu 143`（6 目录 + 40 C + 97 F）/ `sys_role_menu 385` / `sys_user_role 30`；内控 40/40/60/60/4/20/20/30/25
  - 6 角色 permissions：admin 76（内控只读）/ icpadmin 101 / auditor 47 / exec 43 / dept01 63 / staff01 24
- **M2a 验收结论**：通过（详见 `docs/07-M2a-验收报告.md`）：组织权限 9 组接口 + 数据权限（1/2/3/4，5 暂缓）+ 登录安全 + 在线会话 + 审计查询 + 附件 + 个人中心；API 87/87、Lead 57/57 ×3、前端 157/157
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`；前端 dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M2）**：
  1. M2c 流程协同：Flowable 7 集成 + 6 条 BPMN、待办/已办/催办/监控/历史、`seed-flow.ps1`，并消除 M2b 的 `pendingWorkflow` 过渡（D28）
  2. M2d 协同运营：任务/文档/公告消息(SSE)/日程
  3. M2e 看板与预警：覆盖率/风险分布/整改率/流程耗时/预警、定时任务
- **下一步（1~3 条）**：
  1. 启动 M2c：先 Flowable 7 单流程 spike（制度发布），再落 6 条 BPMN 定义与表单绑定、待办/历史接口与页面
  2. 将 M2b 的 `POST /policies/{id}/submit`、`POST /evaluations/{id}/submit` 改接流程实例（返回 processInstanceId，去除 pendingWorkflow 过渡）
  3. M2c 验收：API 用例 + 前端真实回归 + 数据权限/权限拒绝 + 截图与 docs/09-M2c 验收报告
- **已知限制**：工作台统计/图表暂为静态演示数据（M2e 接真实接口）；Redis/MinIO 以 Caffeine+本地存储替代（接口已留）；Docker Compose 仅交付文件不本机实测；自定义数据权限（5）暂缓；矩阵唯一键与逻辑删除冲突（docs/08 §8）；风险阈值配置键未入种子（用默认 15/8）
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M2c：docs/01 §3、docs/03 §5、docs/04 §4、docs/05）
- **更新时间**：2026-11-03
