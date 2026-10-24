-- =====================================================================
-- V1.0.7 批量导入权限点
-- 客户档案 / 合同管理新增 Excel 批量导入能力
-- =====================================================================

INSERT INTO iam_permission (id, code, name, type, parent_code, sort) VALUES
  (108, 'customer:import', '批量导入客户', 'BUTTON', NULL, 17),
  (209, 'contract:import', '批量导入合同', 'BUTTON', NULL, 28);

-- R01 销售：可导入自己创建的客户与合同
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (1013, 1, 'customer:import'),
  (1014, 1, 'contract:import');

-- R02 销售主管：本部门客户与合同导入
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (2009, 2, 'customer:import'),
  (2010, 2, 'contract:import');

-- R05 系统管理员：新增权限点同步授予（超管亦有 * 通配）
INSERT INTO iam_role_permission (id, role_id, permission_code) VALUES
  (5108, 5, 'customer:import'),
  (5209, 5, 'contract:import');
