-- =====================================================================
-- V1.0.8 回款计划合计校验 / 实际回款期数 / 收入确认管理
-- =====================================================================

-- 1) 实际回款增加「期数」字段，用于按「合同 + 期数」自动核销回款计划
ALTER TABLE payment_record
    ADD COLUMN period_no INT NULL COMMENT '关联回款计划期数' AFTER contract_id,
    ADD KEY idx_record_contract_period (contract_id, period_no);

-- 2) 收入确认明细表
CREATE TABLE revenue_record (
    id             BIGINT         NOT NULL,
    contract_id    BIGINT         NOT NULL,
    period_month   VARCHAR(7)     NOT NULL COMMENT '收入归属期间 YYYY-MM',
    recognize_date DATE           NOT NULL COMMENT '收入确认日期',
    income_type    VARCHAR(16)    NOT NULL DEFAULT 'SERVICE' COMMENT 'GOODS/SERVICE/OTHER',
    amount         DECIMAL(18, 2) NOT NULL COMMENT '含税收入金额',
    tax_rate       DECIMAL(5, 4)  NOT NULL DEFAULT 0 COMMENT '税率 0~1',
    tax_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '税额',
    net_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '不含税金额',
    cost_amount    DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '成本金额',
    gross_profit   DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '毛利',
    voucher_no     VARCHAR(255)   NULL COMMENT '单据号',
    remark         VARCHAR(500)   NULL,
    created_by     BIGINT,
    created_at     DATETIME       NOT NULL,
    updated_by     BIGINT,
    updated_at     DATETIME       NOT NULL,
    is_deleted     TINYINT        NOT NULL DEFAULT 0,
    version        INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_revenue_contract (contract_id, is_deleted),
    KEY idx_revenue_date (contract_id, recognize_date),
    KEY idx_revenue_period (period_month),
    KEY idx_revenue_voucher (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收入确认明细';

-- 3) 权限点
INSERT INTO iam_permission (id, code, name, type, parent_code, sort) VALUES
  (308, 'payment:plan_import', '批量导入回款计划', 'BUTTON', NULL, 37),
  (601, 'revenue:list',        '收入确认列表',     'BUTTON', NULL, 60),
  (602, 'revenue:create',      '新增收入明细',     'BUTTON', NULL, 61),
  (603, 'revenue:update',      '修改收入明细',     'BUTTON', NULL, 62),
  (604, 'revenue:delete',      '删除收入明细',     'BUTTON', NULL, 63),
  (605, 'revenue:import',      '批量导入收入',     'BUTTON', NULL, 64);

-- 4) 角色授权
-- R01 销售：可导入回款计划、可查看收入确认
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (1015, 1, 'payment:plan_import'),
  (1016, 1, 'revenue:list');

-- R02 销售主管：可查看收入确认
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (2011, 2, 'revenue:list');

-- R03 财务：回款计划导入 + 收入确认全功能
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (3012, 3, 'payment:plan_import'),
  (3013, 3, 'revenue:list'),
  (3014, 3, 'revenue:create'),
  (3015, 3, 'revenue:update'),
  (3016, 3, 'revenue:delete'),
  (3017, 3, 'revenue:import');

-- R04 高管：只读收入确认
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (4007, 4, 'revenue:list');

-- R05 系统管理员：同步授予新增权限点（超管亦有 * 通配）
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (5308, 5, 'payment:plan_import'),
  (5601, 5, 'revenue:list'),
  (5602, 5, 'revenue:create'),
  (5603, 5, 'revenue:update'),
  (5604, 5, 'revenue:delete'),
  (5605, 5, 'revenue:import');
