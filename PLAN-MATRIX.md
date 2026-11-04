# 方案—实现追踪矩阵（PLAN-MATRIX）

> 状态取值（M1 起）：`已实现 / 部分 / 暂缓 / 不做`（手册 02）。禁止把暂缓写成已实现。
> 「当前实现」列写明本阶段实际到位的部分；「测试/证据」列只填**已执行**的命令与结果，未执行记「待 Mx」。
> 本文件随阶段验收更新；最新阶段见 STATUS.md。

## 一、方案能力（模块级）

| 方案要求 | 当前实现 | 测试/证据 | 文档位置 | 状态 |
| --- | --- | --- | --- | --- |
| 组织权限：部门/岗位/用户/角色/菜单/按钮 CRUD | 已实现：部门/岗位/用户/角色/菜单/字典/参数/任务 9 模块 CRUD + 重置密码/状态切换/菜单授权（149 节点，随 M2b/M2c 扩展）；种子菜单 149 | Lead verify-m2a 57/57 ×3；前端 84/84 + 48/48；后端 test-api-m2a 79/79 | docs/03、docs/05、docs/07 | 已实现 |
| RBAC + 数据权限（本人/本部门/本部门及下级） | 已实现：`@DataScope` 按 data_scope 1/2/3/4 过滤，5=自定义暂缓 | admin 30 人、dept01 4 人、staff01 1 人、dataScope=2 临时角色仅 6 人；JUnit DataScope 2 项 | docs/05、docs/07 | 已实现 |
| 登录与安全：JWT + Refresh、密码策略、失败锁定、在线用户 | 已实现：access 2h/refresh 7d、BCrypt、5 次失败锁定 40102、在线会话列表/强制下线、改密后旧 token 失效 | 6 角色登录、错误密码、锁定、踢人后 401 实测 | docs/03、docs/07 | 已实现 |
| 系统审计：操作日志、登录日志、数据变更审计、流程留痕 | 已实现：操作/登录/变更三类查询 + traceId 命中；流程发起/审批/驳回/催办落 `sys_oper_log`（module=流程中心） | verify-m2c C23 审计用例 PASS；auditor 可读 | docs/03、docs/07、docs/09 | 已实现 |
| 制度库：分类/版本/生效日期/附件/关联流程/发布审批 | 已实现：分类树 + 制度 40 条 CRUD + submit（真实发起 Flowable，D34）/abolish 状态机 + 制度页 CSV 导出；审批通过回调 status=2 + publish_time | test-api-m2b 47/47；verify-m2c C17 PASS；前端 173/173 | docs/03、docs/04、docs/08、docs/09 | 已实现 |
| 风险库：分类/等级/影响×可能性评分/责任人 | 已实现：分类/风险 CRUD + probability×impact 自动定级（15/8 阈值）+ 启停；40 条种子 | 评分边界 6/8/15 单测；前端评分预览 | docs/03、docs/08 | 已实现 |
| 控制矩阵：风险—控制点—制度—流程—岗位关联与覆盖度 | 已实现：控制点 60/矩阵 60 CRUD + 唯一键冲突提示 + `GET /matrices/coverage`（40 风险全覆盖 100%） | coverage 实测 40/40/0/100.0；矩阵唯一键单测 | docs/03、docs/08 | 已实现（唯一键与逻辑删除冲突为已知限制） |
| 内控评价：计划/任务/评分/底稿 | 已实现：计划 submit/start（按矩阵模板生成任务、幂等）/finish/close + 任务 start/submit + 底稿；种子 4/20/20 | 单测 start 幂等/finish 门禁；前端 52/52 闭环 | docs/03、docs/08 | 已实现 |
| 缺陷登记/分级/整改/证据/复核关闭 | 已实现：缺陷 CRUD/rectify/close + 整改 submit（真实发起流程，D34）/approve/reject/close 闭环；approve→整改 2+缺陷 2、reject→0+缺陷 1 | 前端闭环 52/52；verify-m2c C19/C19b PASS | docs/03、docs/04、docs/08、docs/09 | 已实现 |
| BPMN 定义 + 6 条内置流程 + 表单绑定 | 已实现：Flowable 7.1.0 内嵌 + 6 条 BPMN（classpath 启动部署，deployment=icp-processes，version 回写）；formData JSON + formSchema 只读渲染 | 6 keys/version1/diagram 实测；mvn verify 98 tests；verify-m2c 77/77 | docs/03 §5.1、docs/04、docs/09 | 已实现（无在线设计器，D31） |
| 待办/已办/抄送/催办/流程监控/超时预警 | 已实现：待办/已办/我发起、同意/驳回/催办、monitor 统计与 overdue（timeout.hours=48）；抄送落 `coll_message(CC)` 无独立页 | 6 角色待办 0/40/0/17/4/0；verify-m2c C04–C15 PASS；seed-flow 80/0 | docs/03 §5.1、docs/09 | 已实现 |
| 审批历史留痕/意见/附件；bpmn-js 只读流程图 | 已实现：历史时间线（ACT_HI_*）、审批意见/附件、`/diagram-highlight` + bpmn-js 17.11.1 只读高亮（无 DI 坐标时前端自动布局） | 前端待办详情/高亮用例 PASS；verify-m2c C13/C14 PASS | docs/03、docs/09 | 已实现（一级审批，无加签/转办） |
| 任务中心：计划/任务/子任务/优先级/截止提醒 | 已实现：任务 80（含子任务）/评论 30 + 状态机 0/1/2/3 + 优先级/截止 + 提醒落站内消息（type=3）+ 逾期查询期计算 | test-api-m2d 109/109；verify-m2d 44/44；前端 mock 43/43 | docs/03 §6.1、docs/10 | 已实现 |
| 文档中心：目录/权限/版本/本地下载 | 已实现：分类树/文档 40 CRUD + 发布/归档 + `coll_doc_perm`（type×perm 三元组）+ 下载鉴权（发布→权限→借阅）+ 版本次版本+1 | 独立验收 44/44（未授权 40300→授权 200、版本 V1.1） | docs/03 §6.1、docs/10 | 已实现（无在线预览，预览预留） |
| 公告/站内信（SSE）/日程 | 已实现：公告 12（全公司/部门/角色范围 + 已读幂等 + 关闭）、站内信 60 + 批量已读 + SSE 实时推送（access_token/心跳/3 连接）、日程 20 + 月历 + 取消=3 | SSE 事件/坏 token 401/未读归零实测；verify-m2d 44/44 | docs/03 §6.1、docs/10 | 已实现 |
| 报表看板：覆盖率/风险分布/整改完成率/流程耗时/钻取 | 接口契约与看板占位页已就绪（工作台为静态演示数据）；聚合接口待 M2e | 待 M2e | docs/03 | 部分 |
| 预警：超时任务/到期制度/高风险缺陷 + 定时任务 | 契约与 `sys_job` 5 条种子已定；扫描逻辑待 M2e | 待 M2e | docs/03 | 部分 |
| 审计查询：登录/操作/变更日志（审计员只读） | 已实现：auditor 仅「日志审计」菜单，三 tab 查询 + 详情抽屉 | 前端 7/7；auditor `/logs/oper` 200、`/logs/changes` 200、写接口 403 | docs/03、docs/07 | 已实现 |
| 字典/参数/附件/健康检查 | 已实现：字典/参数 CRUD、附件上传下载（20MB + 类型白名单）、`/actuator/health` UP | 前端 file 8/8；后端 41300 用例；health UP | docs/03、docs/07 | 已实现 |
| 动态表单渲染（字段/校验/布局，JSON Schema） | 已实现只读渲染：待办 formSchema（text/textarea/select/date/number/amount/attachmentList）+ 业务字段合并展示；无在线表单设计器（二期） | 待办详情 formData 回读用例 PASS | docs/03 §5.1、docs/09 | 部分 |
| 数据权限自定义部门范围 | 未开始（字段与枚举 5 已预留） | — | docs/05 | 暂缓 |
| 多租户实装 | 仅 `tenant_id` 字段预留 | — | docs/04 | 暂缓 |
| OAuth2/LDAP/AD、电子签名 | 未开始（契约标注预留） | — | docs/03 | 暂缓 |
| bpmn-js 在线设计器、低代码表单设计器 | 未开始（一期仅只读查看） | — | — | 暂缓 |
| ES 全文检索、在线文档预览/编辑、委托转办 | 未开始 | — | — | 暂缓 |
| 移动端 App/H5/小程序 | 不做（前端仅响应式适配） | — | docs/00 | 不做 |
| 合同/采购/费用业务插件 | 未开始（二期） | — | — | 暂缓 |
| K8s/Helm、Prometheus/Grafana/Loki、GitLab CI | 未开始（二期，目录 M4 预留） | — | docs/10 | 暂缓 |
| 真实 Redis / MinIO / 消息队列 | 进程内 Caffeine + 本地存储替代，接口已留（D06/D07） | 缓存/存储类已实现 | docs/00、docs/10 | 暂缓 |
| Docker Compose 一键部署 | 文件待 M4 交付；本机无 Docker 不实测 | 待 M4 | deploy/、docs/10 | 部分 |
| 本地一键启动（MySQL 3307 + 后端 + 前端） | 分步脚本已实现（mysql-start/db-reset/start-backend/dev-frontend）；一键 start-local 待 M4 | 脚本实测通过 | docs/10 | 部分 |
| 备份/恢复脚本与一次恢复演练 | 待 M4 | 待 M4 | docs/10 | 部分 |
| 60 页源码/60 页手册/申请表（M5） | 一期不做（用户确认 Q3） | — | docs/12 | 暂缓 |

## 二、验收场景（方案 §九）

| 验收场景 | 当前实现 | 测试/证据 | 文档位置 | 状态 |
| --- | --- | --- | --- | --- |
| ① 30 分钟一键部署（本地等效） | 分步脚本已可复现（M1 实测 db-reset 10s + 构建 + 启动）；一键脚本待 M4 | 复现步骤见 docs/06 §8 | docs/10、docs/09 | 部分 |
| ② 登录 / 菜单 / 数据权限 | 登录/菜单/按钮/数据权限已实测 6 角色 + 临时 dataScope=2 角色 | Lead 57/57 ×3；前端 84/84、dataScope 10/10 | docs/05、docs/07 | 已实现 |
| ③ 内控闭环（制度→…→复核关闭） | 已实现：制度发布→风险/控制→矩阵→评价计划/任务/底稿→缺陷→整改→复核关闭全链路页面前后端打通（submit 过渡 pendingWorkflow，M2c 接 Flowable） | 前端 52/52 闭环 + Lead API 探针；icpadmin 全链路实测 | docs/03、docs/08 | 已实现 |
| ④ 审批待办 / 催办 / 留痕 | 已实现：6 条流程可发起→同意/驳回→完成，待办/已办/我发起隔离，催办落库+消息，历史与高亮可查 | 后端 70/70；Lead 77/77 ×3；前端 173/173；截图 shots\m2c | docs/09、docs/03 §5.1 | 已实现 |
| ⑤ 审计可查可追溯 | 操作/登录/变更三类可查 + traceId 追溯；哈希链留 M3 评估 | Lead 57/57（A27–A30）；auditor 三 tab 前端 7/7 | docs/03、docs/07 | 已实现 |
| ⑥ 备份恢复演练 | 待 M4 | 待 M4 | docs/10、docs/09 | 部分 |

## 三、交付物

| 交付物 | 当前实现 | 测试/证据 | 文档位置 | 状态 |
| --- | --- | --- | --- | --- |
| 项目画像 | 已完成（M0） | 人工评审 | docs/00 | 已实现 |
| 阶段计划与验收口径 | 已完成（M0，M1 同步种子与流程数据说明） | 人工评审 | docs/01 | 已实现 |
| 差距/假设/风险 | 已完成（M0，Q1–Q5 已确认） | 人工评审 | docs/02 | 已实现 |
| 接口契约 | 已完成并冻结（M1） | 6 角色接口实测 + 前端联调 47/47 | docs/03 | 已实现 |
| 数据模型与种子 | 已完成并冻结（42 表、种子 15/15 校验、幂等重建） | db-reset ×2、db-validate 15/15 | docs/04 | 已实现 |
| 权限矩阵 | 已完成并冻结（6 角色 × 菜单/按钮/数据范围 + A01–A12） | 6 角色实测 + 允许/拒绝抽查 | docs/05 | 已实现 |
| 后端骨架 | 已完成（构建、健康、认证 4 接口、审计 AOP、Flyway、Swagger） | mvn verify、health、登录实测 | docs/06 | 已实现 |
| 前端骨架 | 已完成（登录、主框架、动态路由、v-perm、403/404、工作台占位） | npm build、Playwright 47/47 | docs/06 | 已实现 |
| 系统管理 9 页 + 个人中心（M2a） | 已完成（dept/post/user/role/menu/dict/config/log/job + profile，真实后端） | 前端 84/84 + 48/48 + 8/8 + 10/10 + 7/7；Lead API 57/57 ×3 | docs/07 | 已实现 |
| 内控业务 7 页（M2b） | 已完成（制度/风险/控制点/矩阵/评价+任务/缺陷/整改，真实后端，含 CSV 导出） | 前端 87/18/52；后端 96 单测；test-api-m2b 47/47 | docs/08 | 已实现 |
| 流程中心 9 页（M2c） | 已完成（待办/已办/我发起/监控/实例/定义/采购/用印/借阅 + 详情抽屉 + bpmn 高亮） | 前端 69/55/28/21；verify-m2c 77/77 ×3 | docs/09 | 已实现 |
| 协同运营 6 页（M2d） | 已完成（任务/文档/借阅/公告/消息 SSE/日程月历 + 权限抽屉） | verify-m2d 44/44；test-api-m2d 109/109；前端 mock 43/43 | docs/10 | 已实现 |
| 各阶段验收报告 | M1、M2a、M2b、M2c、M2d 报告已完成；M2e–M4 待阶段结束 | docs/06–10 实际命令与数字 | docs/06–10 | 部分 |
| 最终截图（≥25 张） | M1 联调截图为证据（仓库外）；正式截图库待 M3 | 截图清单待 M3 | docs/screenshots | 部分 |
| 部署运维手册 / 用户手册 | 待 M4 | 待 M4 | docs/10、docs/11 | 部分 |
