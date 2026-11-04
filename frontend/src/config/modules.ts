import type { MenuItem } from '@/types'
import { collectMenuPaths } from '@/utils/menu'

/**
 * M1 前端骨架内置模块菜单（与真实后端 /api/v1/auth/login 返回的菜单树保持一致）。
 * 仅用于「后端未就绪/菜单为空」时的兜底，避免白屏；登录成功后以后端 menus 为准。
 * 注意：子菜单 path 后端返回不带前导 /，路由注册前会统一归一化。
 */
export const ALL_MENUS: MenuItem[] = [
  {
    id: 1,
    parentId: 0,
    name: '系统管理',
    path: '/system',
    icon: 'Setting',
    type: 'M',
    children: [
      { id: 10, parentId: 1, name: '部门管理', path: '/system/dept', icon: 'OfficeBuilding', type: 'C' },
      { id: 11, parentId: 1, name: '岗位管理', path: '/system/post', icon: 'Avatar', type: 'C' },
      { id: 12, parentId: 1, name: '用户管理', path: '/system/user', icon: 'User', type: 'C' },
      { id: 13, parentId: 1, name: '角色管理', path: '/system/role', icon: 'UserFilled', type: 'C' },
      { id: 14, parentId: 1, name: '菜单管理', path: '/system/menu', icon: 'Menu', type: 'C' },
      { id: 15, parentId: 1, name: '字典管理', path: '/system/dict', icon: 'Collection', type: 'C' },
      { id: 16, parentId: 1, name: '参数设置', path: '/system/config', icon: 'Tools', type: 'C' },
      { id: 17, parentId: 1, name: '操作日志', path: '/system/log', icon: 'Document', type: 'C' }
    ]
  },
  {
    id: 2,
    parentId: 0,
    name: '内控管理',
    path: '/icp',
    icon: 'Lock',
    type: 'M',
    children: [
      { id: 20, parentId: 2, name: '制度库', path: '/icp/policy', icon: 'Notebook', type: 'C' },
      { id: 21, parentId: 2, name: '风险库', path: '/icp/risk', icon: 'Warning', type: 'C' },
      { id: 22, parentId: 2, name: '控制点', path: '/icp/control', icon: 'Operation', type: 'C' },
      { id: 23, parentId: 2, name: '控制矩阵', path: '/icp/matrix', icon: 'Grid', type: 'C' },
      { id: 24, parentId: 2, name: '评价计划', path: '/icp/evaluation', icon: 'DocumentChecked', type: 'C' },
      { id: 25, parentId: 2, name: '缺陷管理', path: '/icp/defect', icon: 'FirstAidKit', type: 'C' },
      { id: 26, parentId: 2, name: '整改管理', path: '/icp/rectify', icon: 'Tools', type: 'C' }
    ]
  },
  {
    id: 3,
    parentId: 0,
    name: '协同办公',
    path: '/coll',
    icon: 'ChatDotRound',
    type: 'M',
    children: [
      { id: 30, parentId: 3, name: '任务管理', path: '/coll/task', icon: 'List', type: 'C' },
      { id: 31, parentId: 3, name: '文档中心', path: '/coll/doc', icon: 'FolderOpened', type: 'C' },
      { id: 32, parentId: 3, name: '文档借阅', path: '/coll/doc-borrow', icon: 'Reading', type: 'C' },
      { id: 33, parentId: 3, name: '公告通知', path: '/coll/notice', icon: 'Bell', type: 'C' },
      { id: 34, parentId: 3, name: '日程会议', path: '/coll/schedule', icon: 'Calendar', type: 'C' },
      { id: 35, parentId: 3, name: '消息中心', path: '/coll/message', icon: 'Message', type: 'C' }
    ]
  },
  {
    id: 4,
    parentId: 0,
    name: '流程中心',
    path: '/workflow',
    icon: 'Share',
    type: 'M',
    children: [
      { id: 41, parentId: 4, name: '流程定义', path: '/wf/definition', icon: 'Share', type: 'C' },
      { id: 42, parentId: 4, name: '流程实例', path: '/wf/instance', icon: 'Connection', type: 'C' },
      { id: 43, parentId: 4, name: '我的待办', path: '/wf/todo', icon: 'Tickets', type: 'C' },
      { id: 44, parentId: 4, name: '我的已办', path: '/wf/done', icon: 'CircleCheck', type: 'C' },
      { id: 45, parentId: 4, name: '采购申请', path: '/wf/purchase', icon: 'Coin', type: 'C' },
      { id: 46, parentId: 4, name: '用印申请', path: '/wf/seal', icon: 'Stamp', type: 'C' }
    ]
  },
  {
    id: 5,
    parentId: 0,
    name: '报表中心',
    path: '/report',
    icon: 'DataAnalysis',
    type: 'M',
    children: [
      { id: 50, parentId: 5, name: '内控总览', path: '/report/overview', icon: 'DataLine', type: 'C' },
      { id: 51, parentId: 5, name: '制度统计', path: '/report/policy', icon: 'Histogram', type: 'C' },
      { id: 52, parentId: 5, name: '缺陷统计', path: '/report/defect', icon: 'WarningFilled', type: 'C' },
      { id: 53, parentId: 5, name: '任务统计', path: '/report/task', icon: 'TrendCharts', type: 'C' }
    ]
  },
  {
    id: 6,
    parentId: 0,
    name: '管理驾驶舱',
    path: '/dashboard',
    icon: 'Odometer',
    type: 'M',
    children: [
      { id: 60, parentId: 6, name: '管理驾驶舱', path: '/dashboard/index', icon: 'Odometer', type: 'C' },
      { id: 61, parentId: 6, name: '我的工作台', path: '/dashboard/workbench', icon: 'Monitor', type: 'C' },
      { id: 62, parentId: 6, name: '待办提醒', path: '/dashboard/todo', icon: 'Timer', type: 'C' }
    ]
  }
]

/** 已知模块路径集合：用于把「越权访问」判定为 403、未知地址判定为 404 */
export const KNOWN_MENU_PATHS: ReadonlySet<string> = collectMenuPaths(ALL_MENUS)

/** 模块页占位说明（M1 骨架）；M2 按 docs/03 契约替换为真实页面 */
export const MODULE_DESCRIPTIONS: Record<string, string> = {
  '/dashboard': '内控运行总览：待办审批、评价任务、高风险缺陷与制度覆盖率。',
  '/system/dept': '组织部门树维护，支撑数据权限的部门范围划分。',
  '/system/post': '岗位维护与用户岗位分配，作为流程审批人指派依据。',
  '/system/user': '用户账号、部门岗位、角色与状态管理，支持密码重置。',
  '/system/role': '角色与菜单/按钮权限分配，维护 RBAC 权限矩阵。',
  '/system/menu': '菜单、目录与按钮资源维护，控制前端路由与按钮显示。',
  '/system/dict': '数据字典维护，统一业务枚举与显示口径。',
  '/system/config': '系统参数与安全策略配置，记录参数变更留痕。',
  '/system/log': '操作日志、登录日志与关键变更审计，支持按用户与时间检索。',
  '/icp/policy': '制度分类、版本与生效管理，支持制度发布审批及附件归档。',
  '/icp/risk': '风险分类、等级、影响与可能性评分，维护风险责任人与应对措施。',
  '/icp/control': '控制点定义与责任岗位绑定，关联制度条款与执行证据。',
  '/icp/matrix': '风险—控制点—制度—流程—岗位关联，展示控制覆盖度与缺口。',
  '/icp/evaluation': '评价计划、评价任务、评分与底稿管理，支撑内控评价定期开展。',
  '/icp/defect': '缺陷登记与分级、整改任务与证据上传、复核关闭的闭环管理。',
  '/icp/rectify': '整改方案、整改任务与复核审批，跟踪缺陷整改进度与逾期情况。',
  '/coll/task': '协同任务与子任务、优先级与截止提醒，跟踪执行进度。',
  '/coll/doc': '文档目录、权限与版本管理，支持本地下载与借阅审批。',
  '/coll/doc-borrow': '文档借阅申请、审批与归还登记，保留借阅与下载留痕。',
  '/coll/notice': '公告发布与已读回执，站内信消息集中查看。',
  '/coll/schedule': '个人日程与协同安排，支持提醒与冲突检查。',
  '/coll/message': '系统通知与站内消息，支持已读标记与消息归档。',
  '/wf/form': '流程表单模板与字段配置，支撑采购、用印等业务审批。',
  '/wf/definition': '流程定义与节点审批人配置，管理流程版本与启停。',
  '/wf/instance': '流程实例查询与流程图留痕查看，支持流程监控。',
  '/wf/todo': '我的待办：待审批任务集中处理，支持同意、驳回与加签。',
  '/wf/done': '我的已办：历史审批记录与审批意见追溯。',
  '/wf/purchase': '采购申请发起与审批，关联预算、供应商与合同附件。',
  '/wf/seal': '用印申请发起与审批，登记印章类型、用印文件与用印次数。',
  '/wf/urge': '审批催办与超时预警，记录催办对象与催办结果。',
  '/report/overview': '内控覆盖率、风险分布、整改完成率与流程耗时总览。',
  '/report/policy': '制度数量、分类分布、制修订与失效情况统计。',
  '/report/defect': '缺陷等级分布、整改完成率与逾期缺陷分析。',
  '/report/task': '任务完成率、逾期情况与人员负荷统计。',
  '/dashboard/index': '面向管理层的指标驾驶舱，汇总内控运行关键指标。',
  '/dashboard/workbench': '个人工作台：待办、评价任务、缺陷整改与协同事项聚合。',
  '/dashboard/todo': '待办提醒：审批、任务与整改事项的到期提醒。'
}

/** 按菜单分组展示的规划能力（占位页使用，key 为后端一级菜单名） */
export const MODULE_PLANS: Record<string, string[]> = {
  内控管理: [
    '制度分类、版本与生效日期管理，关联发布审批流程',
    '风险等级与影响×可能性评分，关联责任部门与责任人',
    '风险—控制点—制度—流程—岗位关联及覆盖度分析',
    '评价计划/任务/评分/底稿与缺陷整改复核闭环'
  ],
  协同办公: [
    '协同任务、子任务与截止提醒，跟踪执行进度',
    '文档目录、权限、版本与借阅审批，保留下载留痕',
    '公告发布、已读回执与站内消息集中查看',
    '个人日程与会议安排，支持提醒与冲突检查'
  ],
  流程中心: [
    '待办、已办、抄送、催办与超时预警',
    '审批意见、附件上传与全流程历史留痕',
    '采购、用印等业务申请与多级审批',
    '只读流程图查看（bpmn-js），在线设计暂缓'
  ],
  报表中心: [
    '内控覆盖率、风险分布、整改完成率与流程耗时',
    '按部门/时间/风险等级钻取分析',
    '制度、缺陷、任务专题统计与趋势对比',
    '报表导出与定时推送（M2 评估）'
  ],
  管理驾驶舱: [
    '管理层指标卡与趋势图集中展示',
    '个人工作台：待办、任务与整改事项聚合',
    '待办提醒与到期预警',
    '数据范围按角色自动过滤（前端仅展示）'
  ],
  系统管理: [
    '部门树、岗位、用户账号与状态管理',
    '角色—菜单—按钮权限矩阵与数据权限范围',
    '字典、参数、附件等基础配置',
    '操作日志、登录日志与关键变更审计'
  ]
}
