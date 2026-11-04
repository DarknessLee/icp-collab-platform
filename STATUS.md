# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M3 全量验收与 E2E（进行中）；M0、M1、M2a、M2b、M2c、M2d、M2e 已闭环
- **最新 commit**：M2e 阶段提交待记录（`M2E_COMMIT`）；M2d 提交 `f57c831` + `804047a`（远端恢复后已补推）；M2c `e334734` + `fc97f14`；M2b `fa32065` + `34683e1`；M2a `7f11af8` + `3531d5c`；M1 `1c9570a` + `6325b7a`
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 跟踪 origin/master）
- **M2e 验收结论**：通过（详见 `docs/11-M2e-验收报告.md`）
  - 交付：`/dashboard/summary|trend`、报表 6 端点（icp-coverage/risk-distribution/rectify-rate/process-duration/defects/tasks）、`/alerts` + `/alerts/count`、`/jobs` lastRunTime/lastResult + 真实 run；5 个定时 handler（制度到期/评价任务逾期/任务提醒/缺陷超期/公告定时发布）进程内 `@Scheduled` 10min 扫描
  - 证据：`mvn clean verify` **169 测试**全绿；`test-api-m2e.ps1` **62/0**（纯种子）；Lead 独立 `verify-m2e.ps1` **46/46 ×2**（clean seed）；前端 `m2e-real-pages` **126/126**、`m2e-real-consistency` **47/47**、截图 43 张；`db-validate` 15/15；旧域回归 m2a 87 / m2b 47 / m2d 109 / m2c 70
  - 干净基线数字：policies 40/有效 30、risks 40/高 12、coverage 40/40@100.0、defects 30/未关闭 20、rectify 25/10@40.0、tasks 80（8/12/60/0，overdue 5）、alerts 5/0/6/0=11；6 角色 perms 95/137/62/60/104/51
  - 种子**零改动**：`sys_menu 179` / `sys_role_menu 540` / `sys_user_role 30` / `sys_config 16`；无 V4 迁移
  - 修复真实缺陷：job 审计 `trace_id` 为空（手动 run 沿用 MDC，定时线程生成 32 位 hex，历史回填）
- **M2d 验收结论**：通过（详见 `docs/10-M2d-验收报告.md`）：协同 6 组接口（任务/文档/借阅/公告/消息 SSE/日程）；后端 147 测试、API 109/109、Lead 44/44、前端 276/276；协同按钮 202–231、6 角色 collab 权限 16/43/42/26/16/16；修复系统消息 AND 判定缺陷
- **M2c 验收结论**：通过（详见 `docs/09-M2c-验收报告.md`）：Flowable 7.1.0 内嵌 + 6 条 BPMN；待办/已办/我发起/催办/监控/历史/只读高亮；三个 submit 真实发起流程（D28 消除）；后端 98 测试、API 70/70、Lead 77/77 ×3、前端 69/55/28/21；演示态 60 实例/40 待办/20 催办
- **M2b 验收结论**：通过（详见 `docs/08-M2b-验收报告.md`）：制度→风险→控制矩阵→评价/任务/底稿→缺陷→整改复核关闭 全链路；后端 96 测试、API 47/47、Lead 43/43 ×2、前端 87/18/52
- **M2a 验收结论**：通过（详见 `docs/07-M2a-验收报告.md`）：组织权限 9 组接口 + 数据权限（1/2/3/4，5 暂缓）+ 登录安全 + 在线会话 + 审计查询 + 附件 + 个人中心；API 87/87、Lead 57/57 ×3、前端 157/157
- **当前运行环境**（本机可复现）：
  - MySQL 项目实例 `127.0.0.1:3307`（datadir `.runtime\mysql`，库 `icp_platform`，账号 `icp_app`），系统 MySQL80/3306 未动
  - 后端 `http://127.0.0.1:8080`（Flowable 7.1.0，ACT_* 39 张）；前端 dev `http://127.0.0.1:5173`
  - 演示账号：`admin` / `icpadmin` / `auditor` / `exec` / `dept01`–`dept06` / `staff01`–`staff20`，密码 `Icp@2026`
- **未完成（M3/M4）**：
  1. M3 全量验收：全量 API 回归 + Playwright E2E（6 条验收场景）+ 一致性复核 + ≥25 张最终截图 + `docs/12-M3-验收报告.md`
  2. M4 交付：`deploy/`（docker-compose/.env.example/nginx/README）、`start-local` 冷启动、备份恢复、文档收敛、`check-docs`
  3. M5 软著材料：一期不做（D11）
- **下一步（1~3 条）**：
  1. 冻结 M3 验收口径（docs/01 §3）：E2E 场景清单、API 全量清单、截图清单
  2. 分派 E2E（Playwright，写 `e2e/`）与全量 API 回归任务，Lead 独立复验
  3. 写 docs/12-M3 验收报告 + 最终演示态（reset + seed-flow）+ M3 提交推送
- **已知限制**：Redis/MinIO 以 Caffeine+本地存储替代；Docker Compose 仅交付文件不本机实测；自定义数据权限（5）暂缓；流程仅一级审批（无加签/转办/在线设计器）且含「发起」节点需发起人确认（docs/10 §7.1）；矩阵唯一键与逻辑删除冲突（docs/08 §8）；文档可见性非 ADMIN 仅创建人/授权/借阅三种来源；SSE 单实例内存注册；定时器不解析 cronExpression、lastRunTime 为进程内状态；报表为实时聚合
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（M3：docs/01 §3、docs/03、docs/04、docs/05、docs/11 §10）
- **更新时间**：2026-11-04
