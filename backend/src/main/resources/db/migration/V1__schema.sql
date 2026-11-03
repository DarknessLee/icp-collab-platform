-- ============================================================
-- ICP Platform M1 schema (docs/04 §3), Flyway V1
-- MySQL 8.0.46 / utf8mb4 / InnoDB / lower_case_table_names=1
-- 42 business tables. Flowable ACT_* tables are created by the engine.
-- Unified columns: tenant_id, create_by, create_time, update_by,
--                  update_time, deleted (docs/04 §1).
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 3.1 System domain (sys_)
-- ------------------------------------------------------------
CREATE TABLE sys_dept (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，根为0',
    ancestors     VARCHAR(500) NULL COMMENT '祖级列表，如 0,1',
    dept_name     VARCHAR(64)  NOT NULL COMMENT '部门名称',
    dept_code     VARCHAR(32)  NOT NULL COMMENT '部门编码',
    order_num     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    leader_user_id BIGINT      NULL COMMENT '负责人用户ID',
    phone         VARCHAR(20)  NULL COMMENT '联系电话',
    email         VARCHAR(100) NULL COMMENT '邮箱',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark        VARCHAR(255) NULL COMMENT '备注',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门树';

CREATE TABLE sys_post (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_code   VARCHAR(32)  NOT NULL COMMENT '岗位编码',
    post_name   VARCHAR(64)  NOT NULL COMMENT '岗位名称',
    order_num   INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark      VARCHAR(255) NULL COMMENT '备注',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_code (post_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位';

CREATE TABLE sys_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dept_id         BIGINT       NULL COMMENT '部门ID',
    post_id         BIGINT       NULL COMMENT '岗位ID',
    username        VARCHAR(32)  NOT NULL COMMENT '登录名',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    real_name       VARCHAR(64)  NULL COMMENT '真实姓名',
    gender          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    mobile          VARCHAR(20)  NULL COMMENT '手机号',
    email           VARCHAR(100) NULL COMMENT '邮箱',
    avatar          VARCHAR(255) NULL COMMENT '头像',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用 2锁定',
    login_ip        VARCHAR(64)  NULL COMMENT '最后登录IP',
    login_time      DATETIME     NULL COMMENT '最后登录时间',
    pwd_update_time DATETIME     NULL COMMENT '密码更新时间',
    fail_count      INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_until      DATETIME     NULL COMMENT '锁定截止时间',
    remark          VARCHAR(255) NULL COMMENT '备注',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by       BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_dept_id (dept_id),
    KEY idx_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(32)  NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    role_sort   INT          NOT NULL DEFAULT 0 COMMENT '排序',
    data_scope  TINYINT      NOT NULL DEFAULT 1 COMMENT '数据范围 1本人 2本部门 3本部门及下级 4全部 5自定义',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark      VARCHAR(255) NULL COMMENT '备注',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色';

CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，根为0',
    menu_name   VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    menu_type   CHAR(1)      NOT NULL COMMENT '类型 M目录 C菜单 F按钮',
    path        VARCHAR(200) NULL COMMENT '路由地址',
    component   VARCHAR(200) NULL COMMENT '组件路径',
    perms       VARCHAR(100) NULL COMMENT '权限标识',
    icon        VARCHAR(64)  NULL COMMENT '图标',
    order_num   INT          NOT NULL DEFAULT 0 COMMENT '排序',
    visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示 1是 0否',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单/按钮';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-角色';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-菜单';

CREATE TABLE sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-部门(自定义数据权限)';

CREATE TABLE sys_dict_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_name   VARCHAR(64)  NOT NULL COMMENT '字典名称',
    dict_type   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark      VARCHAR(255) NULL COMMENT '备注',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型';

CREATE TABLE sys_dict_data (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_type   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    dict_label  VARCHAR(64)  NOT NULL COMMENT '字典标签',
    dict_value  VARCHAR(64)  NOT NULL COMMENT '字典值',
    dict_sort   INT          NOT NULL DEFAULT 0 COMMENT '排序',
    tag_type    VARCHAR(20)  NULL COMMENT '标签样式',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark      VARCHAR(255) NULL COMMENT '备注',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据';

CREATE TABLE sys_config (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_name  VARCHAR(64)   NOT NULL COMMENT '参数名称',
    config_key   VARCHAR(100)  NOT NULL COMMENT '参数键',
    config_value VARCHAR(1000) NULL COMMENT '参数值',
    config_type  CHAR(1)       NOT NULL DEFAULT 'N' COMMENT 'Y内置 N可改',
    remark       VARCHAR(255)  NULL COMMENT '备注',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by    BIGINT        NULL COMMENT '创建人',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT        NULL COMMENT '更新人',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统参数';

CREATE TABLE sys_file (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name     VARCHAR(200) NOT NULL COMMENT '存储文件名',
    original_name VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_suffix   VARCHAR(20)  NULL COMMENT '后缀',
    file_path     VARCHAR(500) NOT NULL COMMENT '相对存储根路径',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    content_type  VARCHAR(100) NULL COMMENT 'MIME',
    storage_type  VARCHAR(10)  NOT NULL DEFAULT 'local' COMMENT 'local/minio',
    bucket        VARCHAR(64)  NULL COMMENT '桶名',
    biz_type      VARCHAR(50)  NULL COMMENT '业务类型',
    biz_id        BIGINT       NULL COMMENT '业务ID',
    upload_by     BIGINT       NULL COMMENT '上传人',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_upload_by (upload_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='附件';

CREATE TABLE sys_oper_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id       VARCHAR(64)   NULL COMMENT '链路追踪ID',
    title          VARCHAR(100)  NULL COMMENT '操作标题',
    business_type  VARCHAR(20)   NULL COMMENT '业务类型',
    method         VARCHAR(200)  NULL COMMENT '方法',
    request_method VARCHAR(10)   NULL COMMENT 'HTTP方法',
    request_uri    VARCHAR(255)  NULL COMMENT '请求URI',
    request_param  TEXT          NULL COMMENT '请求参数',
    status         TINYINT       NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
    error_msg      VARCHAR(2000) NULL COMMENT '错误信息',
    oper_user_id   BIGINT        NULL COMMENT '操作人ID',
    oper_name      VARCHAR(64)   NULL COMMENT '操作人',
    dept_id        BIGINT        NULL COMMENT '部门ID',
    oper_ip        VARCHAR(64)   NULL COMMENT '操作IP',
    user_agent     VARCHAR(500)  NULL COMMENT 'UA',
    cost_ms        INT           NOT NULL DEFAULT 0 COMMENT '耗时ms',
    oper_time      DATETIME      NULL COMMENT '操作时间',
    tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by      BIGINT        NULL COMMENT '创建人',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新人',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_oper_time (oper_time),
    KEY idx_oper_user (oper_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志';

CREATE TABLE sys_login_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NULL COMMENT '用户名',
    user_id     BIGINT       NULL COMMENT '用户ID',
    ip          VARCHAR(64)  NULL COMMENT 'IP',
    user_agent  VARCHAR(500) NULL COMMENT 'UA',
    login_type  VARCHAR(10)  NOT NULL DEFAULT 'login' COMMENT 'login/logout',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    msg         VARCHAR(255) NULL COMMENT '消息',
    login_time  DATETIME     NULL COMMENT '登录时间',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_login_time (login_time),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录日志';

CREATE TABLE sys_job (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_name        VARCHAR(100) NOT NULL COMMENT '任务名称',
    job_group       VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组',
    cron_expression VARCHAR(50)  NOT NULL COMMENT 'cron 表达式',
    invoke_target   VARCHAR(200) NOT NULL COMMENT '调用目标',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark          VARCHAR(255) NULL COMMENT '备注',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by       BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_name (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务';

-- ------------------------------------------------------------
-- 3.2 Internal control domain (icp_)
-- ------------------------------------------------------------
CREATE TABLE icp_policy_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID',
    category_name VARCHAR(64)  NOT NULL COMMENT '分类名称',
    order_num     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='制度分类';

CREATE TABLE icp_policy (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    policy_code     VARCHAR(32)  NOT NULL COMMENT '制度编号 ZD-yyyy-xxxx',
    policy_name     VARCHAR(200) NOT NULL COMMENT '制度名称',
    category_id     BIGINT       NOT NULL COMMENT '分类ID',
    version         VARCHAR(20)  NOT NULL DEFAULT 'V1.0' COMMENT '版本',
    effective_date  DATE         NULL COMMENT '生效日期',
    expire_date     DATE         NULL COMMENT '失效日期',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2已生效 3已废止 4已过期',
    content         LONGTEXT     NULL COMMENT '正文(HTML)',
    owner_dept_id   BIGINT       NULL COMMENT '归口部门',
    owner_user_id   BIGINT       NULL COMMENT '负责人',
    keywords        VARCHAR(200) NULL COMMENT '关键词',
    flow_instance_id VARCHAR(64) NULL COMMENT '流程实例ID',
    publish_time    DATETIME     NULL COMMENT '发布时间',
    remark          VARCHAR(255) NULL COMMENT '备注',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by       BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_code (policy_code),
    KEY idx_category (category_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='制度库';

CREATE TABLE icp_risk_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID',
    category_name VARCHAR(64)  NOT NULL COMMENT '分类名称',
    order_num     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风险分类';

CREATE TABLE icp_risk (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    risk_code     VARCHAR(32)   NOT NULL COMMENT '风险编号 FX-yyyy-nnn',
    risk_name     VARCHAR(200)  NOT NULL COMMENT '风险名称',
    category_id   BIGINT        NOT NULL COMMENT '分类ID',
    risk_level    TINYINT       NOT NULL DEFAULT 3 COMMENT '1高 2中 3低',
    probability   TINYINT       NOT NULL DEFAULT 3 COMMENT '可能性 1-5',
    impact        TINYINT       NOT NULL DEFAULT 3 COMMENT '影响 1-5',
    risk_score    INT           NOT NULL DEFAULT 9 COMMENT '风险值=probability*impact',
    owner_dept_id BIGINT        NULL COMMENT '责任部门',
    owner_user_id BIGINT        NULL COMMENT '责任人',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    description   VARCHAR(1000) NULL COMMENT '描述',
    remark        VARCHAR(255)  NULL COMMENT '备注',
    tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT        NULL COMMENT '创建人',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT        NULL COMMENT '更新人',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_code (risk_code),
    KEY idx_category (category_id),
    KEY idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风险库';

CREATE TABLE icp_control (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    control_code  VARCHAR(32)   NOT NULL COMMENT '控制点编号 KZ-yyyy-nnn',
    control_name  VARCHAR(200)  NOT NULL COMMENT '控制点名称',
    control_type  TINYINT       NOT NULL DEFAULT 1 COMMENT '1预防 2发现 3纠正',
    control_desc  VARCHAR(1000) NULL COMMENT '控制描述',
    freq          VARCHAR(50)   NULL COMMENT '执行频率',
    owner_dept_id BIGINT        NULL COMMENT '责任部门',
    owner_user_id BIGINT        NULL COMMENT '责任人',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark        VARCHAR(255)  NULL COMMENT '备注',
    tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT        NULL COMMENT '创建人',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT        NULL COMMENT '更新人',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_control_code (control_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='控制点';

CREATE TABLE icp_matrix (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    matrix_code   VARCHAR(32)  NOT NULL COMMENT '矩阵编号 JC-yyyy-nnn',
    risk_id       BIGINT       NOT NULL COMMENT '风险ID',
    control_id    BIGINT       NOT NULL COMMENT '控制点ID',
    policy_id     BIGINT       NULL COMMENT '制度ID',
    process_key   VARCHAR(64)  NULL COMMENT '关联流程',
    post_id       BIGINT       NULL COMMENT '执行岗位',
    coverage      TINYINT      NOT NULL DEFAULT 1 COMMENT '1全覆盖 2部分 3未覆盖',
    evidence_desc VARCHAR(500) NULL COMMENT '证据说明',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark        VARCHAR(255) NULL COMMENT '备注',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_matrix_code (matrix_code),
    UNIQUE KEY uk_risk_control (risk_id, control_id),
    KEY idx_risk (risk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='控制矩阵';

CREATE TABLE icp_evaluation (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_code        VARCHAR(32)   NOT NULL COMMENT '计划编号 PJ-yyyy-nn',
    plan_name        VARCHAR(200)  NOT NULL COMMENT '计划名称',
    plan_year        SMALLINT      NOT NULL COMMENT '年度',
    plan_type        TINYINT       NOT NULL DEFAULT 1 COMMENT '1年度 2专项',
    start_date       DATE          NOT NULL COMMENT '开始日期',
    end_date         DATE          NOT NULL COMMENT '结束日期',
    dept_scope       VARCHAR(500)  NULL COMMENT '部门范围',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2进行中 3已完成 4已关闭',
    owner_dept_id    BIGINT        NULL COMMENT '牵头部门',
    owner_user_id    BIGINT        NULL COMMENT '负责人',
    flow_instance_id VARCHAR(64)   NULL COMMENT '流程实例ID',
    description      VARCHAR(1000) NULL COMMENT '描述',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by        BIGINT        NULL COMMENT '创建人',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT        NULL COMMENT '更新人',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_code (plan_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价计划';

CREATE TABLE icp_evaluation_task (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_code      VARCHAR(32)   NOT NULL COMMENT '任务编号 PJ-yyyy-nn-nn',
    plan_id        BIGINT        NOT NULL COMMENT '计划ID',
    task_name      VARCHAR(200)  NOT NULL COMMENT '任务名称',
    risk_id        BIGINT        NULL COMMENT '风险ID',
    control_id     BIGINT        NULL COMMENT '控制点ID',
    evaluate_dept_id BIGINT      NULL COMMENT '被评价部门',
    evaluator_id   BIGINT        NULL COMMENT '评价人',
    start_date     DATE          NOT NULL COMMENT '开始日期',
    due_date       DATE          NOT NULL COMMENT '截止日期',
    status         TINYINT       NOT NULL DEFAULT 0 COMMENT '0待评价 1进行中 2已完成 3已逾期',
    score          DECIMAL(5,2)  NULL COMMENT '评分',
    conclusion     TINYINT       NULL COMMENT '1有效 2部分有效 3无效',
    remark         VARCHAR(255)  NULL COMMENT '备注',
    tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by      BIGINT        NULL COMMENT '创建人',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT        NULL COMMENT '更新人',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_plan_id (plan_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价任务';

CREATE TABLE icp_evaluation_workpaper (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id       BIGINT        NOT NULL COMMENT '评价任务ID',
    sample_desc   VARCHAR(500)  NULL COMMENT '抽样说明',
    test_desc     VARCHAR(1000) NULL COMMENT '测试过程',
    test_result   TINYINT       NOT NULL DEFAULT 1 COMMENT '1符合 2部分符合 3不符合',
    evidence      VARCHAR(500)  NULL COMMENT '证据(sys_file id 列表)',
    conclusion    VARCHAR(1000) NULL COMMENT '结论',
    work_user_id  BIGINT        NULL COMMENT '编制人',
    tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT        NULL COMMENT '创建人',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT        NULL COMMENT '更新人',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价底稿';

CREATE TABLE icp_defect (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    defect_code      VARCHAR(32)   NOT NULL COMMENT '缺陷编号 QX-yyyy-nnn',
    defect_name      VARCHAR(200)  NOT NULL COMMENT '缺陷名称',
    source_type      TINYINT       NOT NULL DEFAULT 1 COMMENT '1评价 2审计 3自查 4投诉',
    source_id        BIGINT        NULL COMMENT '来源单据ID',
    severity         TINYINT       NOT NULL DEFAULT 2 COMMENT '1重大 2一般 3轻微',
    defect_type      TINYINT       NOT NULL DEFAULT 2 COMMENT '1设计缺陷 2运行缺陷',
    dept_id          BIGINT        NULL COMMENT '责任部门',
    find_date        DATE          NOT NULL COMMENT '发现日期',
    discover_user_id BIGINT        NULL COMMENT '发现人',
    description      VARCHAR(1000) NULL COMMENT '描述',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0已登记 1整改中 2待复核 3已关闭',
    owner_user_id    BIGINT        NULL COMMENT '整改负责人',
    close_time       DATETIME      NULL COMMENT '关闭时间',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by        BIGINT        NULL COMMENT '创建人',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT        NULL COMMENT '更新人',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_defect_code (defect_code),
    KEY idx_dept (dept_id),
    KEY idx_severity (severity),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='缺陷';

CREATE TABLE icp_rectify (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    rectify_code      VARCHAR(32)   NOT NULL COMMENT '整改编号 ZG-yyyy-nnn',
    defect_id         BIGINT        NOT NULL COMMENT '缺陷ID',
    rectify_plan      VARCHAR(1000) NULL COMMENT '整改计划',
    responsible_user_id BIGINT      NULL COMMENT '整改负责人',
    dept_id           BIGINT        NULL COMMENT '责任部门',
    deadline          DATE          NOT NULL COMMENT '截止日期',
    finish_date       DATE          NULL COMMENT '完成日期',
    evidence          VARCHAR(500)  NULL COMMENT '整改证据(sys_file id 列表)',
    status            TINYINT       NOT NULL DEFAULT 0 COMMENT '0待整改 1待复核 2已通过 3已驳回 4已关闭',
    review_user_id    BIGINT        NULL COMMENT '复核人',
    review_opinion    VARCHAR(500)  NULL COMMENT '复核意见',
    review_time       DATETIME      NULL COMMENT '复核时间',
    flow_instance_id  VARCHAR(64)   NULL COMMENT '流程实例ID',
    remark            VARCHAR(255)  NULL COMMENT '备注',
    tenant_id         BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by         BIGINT        NULL COMMENT '创建人',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by         BIGINT        NULL COMMENT '更新人',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rectify_code (rectify_code),
    KEY idx_defect_id (defect_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='整改任务';

-- ------------------------------------------------------------
-- 3.3 Workflow domain (wf_) - business tables only, no ACT_*
-- ------------------------------------------------------------
CREATE TABLE wf_form (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    form_code   VARCHAR(50)  NOT NULL COMMENT '表单编码',
    form_name   VARCHAR(100) NOT NULL COMMENT '表单名称',
    form_schema JSON         NULL COMMENT '表单 schema',
    version     INT          NOT NULL DEFAULT 1 COMMENT '版本',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    remark      VARCHAR(255) NULL COMMENT '备注',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_code (form_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程表单定义';

CREATE TABLE wf_process_meta (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_key  VARCHAR(64)  NOT NULL COMMENT '流程定义 key',
    process_name VARCHAR(100) NOT NULL COMMENT '流程名称',
    category     VARCHAR(50)  NULL COMMENT '分类',
    version      INT          NOT NULL DEFAULT 1 COMMENT '版本',
    description  VARCHAR(500) NULL COMMENT '说明',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by    BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义元数据';

CREATE TABLE wf_business_link (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_instance_id VARCHAR(64)  NOT NULL COMMENT '流程实例ID',
    process_key         VARCHAR(64)  NOT NULL COMMENT '流程定义 key',
    business_type       VARCHAR(50)  NOT NULL COMMENT '业务类型',
    business_id         BIGINT       NOT NULL COMMENT '业务ID',
    business_no         VARCHAR(50)  NULL COMMENT '业务编号',
    title               VARCHAR(200) NULL COMMENT '标题',
    form_code           VARCHAR(50)  NULL COMMENT '表单编码',
    initiator_id        BIGINT       NULL COMMENT '发起人',
    status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0运行中 1已通过 2已驳回 3已撤回',
    start_time          DATETIME     NULL COMMENT '开始时间',
    end_time            DATETIME     NULL COMMENT '结束时间',
    remark              VARCHAR(255) NULL COMMENT '备注',
    tenant_id           BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by           BIGINT       NULL COMMENT '创建人',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT       NULL COMMENT '更新人',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_instance_id (process_instance_id),
    KEY idx_business (business_type, business_id),
    KEY idx_initiator (initiator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程实例-业务关联';

CREATE TABLE wf_task_urge (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_instance_id VARCHAR(64)  NOT NULL COMMENT '流程实例ID',
    task_id             VARCHAR(64)  NOT NULL COMMENT 'Flowable 任务ID',
    business_no         VARCHAR(50)  NULL COMMENT '业务编号',
    urge_user_id        BIGINT       NULL COMMENT '催办人',
    target_user_id      BIGINT       NULL COMMENT '被催办人',
    content             VARCHAR(200) NULL COMMENT '催办内容',
    urge_time           DATETIME     NULL COMMENT '催办时间',
    tenant_id           BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by           BIGINT       NULL COMMENT '创建人',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT       NULL COMMENT '更新人',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_process_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='催办记录';

CREATE TABLE wf_purchase (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    apply_no         VARCHAR(32)   NOT NULL COMMENT '申请编号 CG-yyyy-nnn',
    title            VARCHAR(200)  NOT NULL COMMENT '标题',
    dept_id          BIGINT        NULL COMMENT '申请部门',
    applicant_id     BIGINT        NULL COMMENT '申请人',
    amount           DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '申请金额',
    budget_amount    DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '预算金额',
    supplier         VARCHAR(100)  NULL COMMENT '供应商',
    purchase_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '1物资 2服务 3工程',
    purpose          VARCHAR(500)  NULL COMMENT '用途',
    apply_date       DATE          NULL COMMENT '申请日期',
    status           TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2已通过 3已驳回 4已完成',
    flow_instance_id VARCHAR(64)   NULL COMMENT '流程实例ID',
    remark           VARCHAR(255)  NULL COMMENT '备注',
    tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by        BIGINT        NULL COMMENT '创建人',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT        NULL COMMENT '更新人',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_apply_no (apply_no),
    KEY idx_dept (dept_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请';

CREATE TABLE wf_seal (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    apply_no         VARCHAR(32)  NOT NULL COMMENT '申请编号 YY-yyyy-nnn',
    seal_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1公章 2合同章 3财务章 4法人章',
    reason           VARCHAR(500) NULL COMMENT '用印事由',
    use_date         DATE         NULL COMMENT '用印日期',
    file_id          BIGINT       NULL COMMENT '附件ID',
    applicant_id     BIGINT       NULL COMMENT '申请人',
    dept_id          BIGINT       NULL COMMENT '申请部门',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2已通过 3已驳回 4已完成',
    flow_instance_id VARCHAR(64)  NULL COMMENT '流程实例ID',
    remark           VARCHAR(255) NULL COMMENT '备注',
    tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by        BIGINT       NULL COMMENT '创建人',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT       NULL COMMENT '更新人',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_apply_no (apply_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用印申请';

-- ------------------------------------------------------------
-- 3.4 Collaboration domain (coll_)
-- ------------------------------------------------------------
CREATE TABLE coll_task (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no      VARCHAR(32)   NOT NULL COMMENT '任务编号 RW-yyyy-xxxx',
    parent_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '父任务ID',
    task_title   VARCHAR(200)  NOT NULL COMMENT '任务标题',
    task_type    TINYINT       NOT NULL DEFAULT 2 COMMENT '1计划 2任务 3子任务',
    priority     TINYINT       NOT NULL DEFAULT 2 COMMENT '1高 2中 3低',
    status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0待办 1进行中 2已完成 3已取消',
    creator_id   BIGINT        NULL COMMENT '创建人',
    assignee_id  BIGINT        NULL COMMENT '负责人',
    dept_id      BIGINT        NULL COMMENT '所属部门',
    plan_start   DATE          NULL COMMENT '计划开始',
    plan_end     DATE          NULL COMMENT '计划结束',
    actual_start DATETIME      NULL COMMENT '实际开始',
    actual_end   DATETIME      NULL COMMENT '实际结束',
    progress     TINYINT       NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    remind_time  DATETIME      NULL COMMENT '提醒时间',
    description  VARCHAR(1000) NULL COMMENT '描述',
    remark       VARCHAR(255)  NULL COMMENT '备注',
    tenant_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by    BIGINT        NULL COMMENT '创建人',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT        NULL COMMENT '更新人',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_assignee (assignee_id),
    KEY idx_status (status),
    KEY idx_plan_end (plan_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='协同任务';

CREATE TABLE coll_task_comment (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT        NOT NULL COMMENT '任务ID',
    user_id     BIGINT        NULL COMMENT '评论人',
    content     VARCHAR(1000) NOT NULL COMMENT '评论内容',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT        NULL COMMENT '创建人',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT        NULL COMMENT '更新人',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务评论';

CREATE TABLE coll_doc_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID',
    category_name VARCHAR(64)  NOT NULL COMMENT '分类名称',
    order_num     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0停用',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by     BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档分类';

CREATE TABLE coll_doc (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_no         VARCHAR(32)  NOT NULL COMMENT '文档编号 WD-yyyy-xxxx',
    doc_name       VARCHAR(200) NOT NULL COMMENT '文档名称',
    category_id    BIGINT       NOT NULL COMMENT '分类ID',
    doc_type       TINYINT      NOT NULL DEFAULT 5 COMMENT '1制度 2模板 3报告 4会议纪要 5其他',
    version        VARCHAR(20)  NOT NULL DEFAULT 'V1.0' COMMENT '版本',
    file_id        BIGINT       NULL COMMENT '文件ID',
    dept_id        BIGINT       NULL COMMENT '所属部门',
    owner_user_id  BIGINT       NULL COMMENT '负责人',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已归档',
    download_count INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
    description    VARCHAR(500) NULL COMMENT '描述',
    remark         VARCHAR(255) NULL COMMENT '备注',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by      BIGINT       NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT       NULL COMMENT '更新人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_no (doc_no),
    KEY idx_category (category_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档';

CREATE TABLE coll_doc_perm (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_id      BIGINT       NOT NULL COMMENT '文档ID',
    target_type TINYINT      NOT NULL COMMENT '1角色 2用户 3部门',
    target_id   BIGINT       NOT NULL COMMENT '目标ID',
    perm_type   TINYINT      NOT NULL COMMENT '1查看 2下载 3编辑',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_perm (doc_id, target_type, target_id, perm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档权限';

CREATE TABLE coll_doc_borrow (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_id           BIGINT       NOT NULL COMMENT '文档ID',
    borrower_id      BIGINT       NULL COMMENT '借阅人',
    reason           VARCHAR(500) NULL COMMENT '借阅原因',
    start_date       DATE         NULL COMMENT '开始日期',
    end_date         DATE         NULL COMMENT '结束日期',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0申请中 1已通过 2已驳回 3已归还',
    flow_instance_id VARCHAR(64)  NULL COMMENT '流程实例ID',
    remark           VARCHAR(255) NULL COMMENT '备注',
    tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by        BIGINT       NULL COMMENT '创建人',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by        BIGINT       NULL COMMENT '更新人',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_doc_id (doc_id),
    KEY idx_borrower (borrower_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档借阅';

CREATE TABLE coll_notice (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    notice_title VARCHAR(200) NOT NULL COMMENT '公告标题',
    notice_type  TINYINT      NOT NULL DEFAULT 2 COMMENT '1通知 2公告 3制度宣贯',
    content      LONGTEXT     NULL COMMENT '正文(HTML)',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已关闭',
    publisher_id BIGINT       NULL COMMENT '发布人',
    publish_time DATETIME     NULL COMMENT '发布时间',
    is_top       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    dept_scope   VARCHAR(500) NULL COMMENT '部门范围',
    remark       VARCHAR(255) NULL COMMENT '备注',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by    BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_publish_time (publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告';

CREATE TABLE coll_notice_read (
    notice_id BIGINT   NOT NULL COMMENT '公告ID',
    user_id   BIGINT   NOT NULL COMMENT '用户ID',
    read_time DATETIME NULL COMMENT '阅读时间',
    PRIMARY KEY (notice_id, user_id),
    KEY idx_notice_read_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告已读';

CREATE TABLE coll_schedule (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    schedule_title VARCHAR(200) NOT NULL COMMENT '日程标题',
    schedule_type  TINYINT      NOT NULL DEFAULT 3 COMMENT '1会议 2提醒 3日程',
    start_time     DATETIME     NOT NULL COMMENT '开始时间',
    end_time       DATETIME     NULL COMMENT '结束时间',
    location       VARCHAR(100) NULL COMMENT '地点',
    participants   VARCHAR(500) NULL COMMENT '参与人(用户ID列表)',
    remind_minutes INT          NOT NULL DEFAULT 15 COMMENT '提前提醒分钟',
    creator_id     BIGINT       NULL COMMENT '创建人',
    dept_id        BIGINT       NULL COMMENT '部门ID',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待开始 1进行中 2已结束 3已取消',
    description    VARCHAR(500) NULL COMMENT '描述',
    remark         VARCHAR(255) NULL COMMENT '备注',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by      BIGINT       NULL COMMENT '创建人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT       NULL COMMENT '更新人',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_start_time (start_time),
    KEY idx_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日程/会议';

CREATE TABLE coll_message (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    message_type TINYINT      NOT NULL DEFAULT 1 COMMENT '1系统 2审批 3任务 4公告 5预警',
    title       VARCHAR(200)  NOT NULL COMMENT '标题',
    content     VARCHAR(1000) NULL COMMENT '内容',
    biz_type    VARCHAR(50)   NULL COMMENT '业务类型',
    biz_id      BIGINT        NULL COMMENT '业务ID',
    sender_id   BIGINT        NULL COMMENT '发送人',
    receiver_id BIGINT        NOT NULL COMMENT '接收人',
    read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_time   DATETIME      NULL COMMENT '阅读时间',
    remark      VARCHAR(255)  NULL COMMENT '备注',
    tenant_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID(预留)',
    create_by   BIGINT        NULL COMMENT '创建人',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT        NULL COMMENT '更新人',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_receiver_read (receiver_id, read_flag),
    KEY idx_type (message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息';
