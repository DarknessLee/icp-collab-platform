# 当前状态

- **软件名称/版本**：企业内控与协同运营管理平台 V1.0（未发表）
- **当前阶段**：M1 契约与数据底座（进行中）；M0 已闭环（用户确认 Q1–Q5）
- **最新 commit**：M1 阶段提交见 `git log`；M0 收尾提交为 `0e9c17b` 系列
- **远端**：`origin` → `http://192.168.1.100:3300/liyunqing/icp-collab-platform.git`（master 已推送）
- **阶段门禁进度（M0）**：画像/计划/差距/假设/不做清单完成；用户确认 Q1–Q5；未写业务代码 ✔
- **M1 待完成**：
  1. `docs/03-接口契约.md`（REST 规范、统一返回、错误码、模块接口字段级定义、状态机）
  2. `docs/04-数据模型与种子.md`（表清单/字段/索引/外键/统一字段、Flyway 版本、种子规模与自洽规则）
  3. `docs/05-权限矩阵.md`（角色 × 菜单/按钮/接口/数据范围，允许+拒绝用例编号）
  4. 运行时：便携 Maven、MySQL 3307 实例脚本（start/stop/reset）、Flyway V1 + 种子
  5. 后端骨架（/actuator/health、统一响应/异常/审计切面、JWT 骨架、OpenAPI）
  6. 前端骨架（登录页 + 主框架 + 动态路由 + 按钮权限）
  7. M1 验收：db-reset 幂等、db-validate、mvn verify、npm build、登录冒烟；`docs/06-M1-验收报告.md`
- **下一步（1~3 条）**：
  1. 写 docs/04 数据模型（含统一字段与状态机）→ docs/03 契约（通用规范 + 模块接口）→ docs/05 权限矩阵
  2. 安装便携 Maven、建 MySQL 3307 实例与 db-reset 脚本，落 Flyway V1/V2 种子
  3. 搭前后端骨架并跑通 M1 验收链
- **阅读入口**：README → STATUS → DECISIONS → PLAN-MATRIX → 当前阶段 docs（03/04/05）
- **更新时间**：2026-11-03
