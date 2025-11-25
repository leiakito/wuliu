# -- 性能优化索引 8.0
# -- 执行此脚本以添加必要的索引，显著提升查询速度
#
# USE logistics;
#
# -- 1. order_record 表优化
# -- SN 查询索引（已有唯一索引 uk_order_sn，无需额外添加）
# -- tracking_number 索引（用于关联查询）
# CREATE INDEX IF NOT EXISTS idx_order_tracking ON order_record(tracking_number);
#
# -- SN 大小写不敏感查询优化（函数索引，MySQL 8.0+）
# -- 用于加速 UPPER(sn) = 'XXX' 查询
# CREATE INDEX IF NOT EXISTS idx_order_sn_upper ON order_record((UPPER(sn)));
#
# -- 2. settlement_record 表优化 - 基础索引
# -- order_id 索引（关键！用于 SN 查询时的 orderId 匹配）
# CREATE INDEX IF NOT EXISTS idx_settlement_order_id ON settlement_record(order_id);
#
# -- tracking_number 索引（用于运单号查询）
# CREATE INDEX IF NOT EXISTS idx_settlement_tracking ON settlement_record(tracking_number);
#
# -- order_time 索引（用于排序）
# CREATE INDEX IF NOT EXISTS idx_settlement_order_time ON settlement_record(order_time);
#
# -- owner_username 索引（用于按归属用户过滤）
# CREATE INDEX IF NOT EXISTS idx_settlement_owner ON settlement_record(owner_username);
#
# -- 3. settlement_record 表优化 - 复合索引
# -- status + order_time 复合索引（用于按状态过滤并排序）
# CREATE INDEX IF NOT EXISTS idx_settlement_status_time ON settlement_record(status, order_time);
#
# -- status + owner_username 复合索引（常见组合查询）
# CREATE INDEX IF NOT EXISTS idx_settlement_status_owner ON settlement_record(status, owner_username);
#
# -- tracking_number + model 复合索引（优化关键字查询）
# CREATE INDEX IF NOT EXISTS idx_settlement_tracking_model ON settlement_record(tracking_number, model);
#
# -- payable_at + status 复合索引（优化日期+状态组合查询）
# CREATE INDEX IF NOT EXISTS idx_settlement_payable_status ON settlement_record(payable_at, status);
#
# -- settle_batch + status 复合索引（优化批次查询）
# CREATE INDEX IF NOT EXISTS idx_settlement_batch_status ON settlement_record(settle_batch, status);
#
# -- id + created_at 复合索引（优化游标分页）
# CREATE INDEX IF NOT EXISTS idx_settlement_id_created ON settlement_record(id, created_at);
#
# -- 4. 查看索引创建结果
# SHOW INDEX FROM order_record;
# SHOW INDEX FROM settlement_record;
#
# -- 5. 分析表统计信息（优化查询计划）
# ANALYZE TABLE order_record;
# ANALYZE TABLE settlement_record;

-- 6. 验证索引使用情况
-- 可以使用以下查询测试索引是否生效
-- EXPLAIN SELECT * FROM settlement_record WHERE order_id IN (SELECT id FROM order_record WHERE UPPER(sn) = 'TEST');
-- EXPLAIN SELECT * FROM settlement_record WHERE status = 'PENDING' ORDER BY order_time DESC LIMIT 20;
-- EXPLAIN SELECT * FROM settlement_record WHERE status = 'PENDING' AND owner_username = 'user1' LIMIT 20;

#
# #5.0
# USE logistics;
#
# -- 1. order_record 表优化
# -- SN 查询索引（已有唯一索引 uk_order_sn，无需额外添加）
# -- tracking_number 索引（用于关联查询）
# -- 如果索引已存在会报错，请忽略或先删除旧索引
# CREATE INDEX idx_order_tracking ON order_record(tracking_number);
#
# -- SN 大小写不敏感查询优化（函数索引，注意版本）
# -- 【注意】：仅 MySQL 8.0.13+ 支持下面这行语法。
# -- 如果你是 MySQL 5.7，请删除下面这行，或者使用虚拟列(Generated Columns)来实现。
# CREATE INDEX idx_order_sn_upper ON order_record((UPPER(sn)));
#
# -- 2. settlement_record 表优化 - 基础索引
# -- order_id 索引（关键！用于 SN 查询时的 orderId 匹配）
# CREATE INDEX idx_settlement_order_id ON settlement_record(order_id);
#
# -- tracking_number 索引（用于运单号查询）
# CREATE INDEX idx_settlement_tracking ON settlement_record(tracking_number);
#
# -- order_time 索引（用于排序）
# CREATE INDEX idx_settlement_order_time ON settlement_record(order_time);
#
# -- owner_username 索引（用于按归属用户过滤）
# CREATE INDEX idx_settlement_owner ON settlement_record(owner_username);
#
# -- 3. settlement_record 表优化 - 复合索引
# -- status + order_time 复合索引（用于按状态过滤并排序）
# CREATE INDEX idx_settlement_status_time ON settlement_record(status, order_time);
#
# -- status + owner_username 复合索引（常见组合查询）
# CREATE INDEX idx_settlement_status_owner ON settlement_record(status, owner_username);
#
# -- tracking_number + model 复合索引（优化关键字查询）
# CREATE INDEX idx_settlement_tracking_model ON settlement_record(tracking_number, model);
#
# -- payable_at + status 复合索引（优化日期+状态组合查询）
# CREATE INDEX idx_settlement_payable_status ON settlement_record(payable_at, status);
#
# -- settle_batch + status 复合索引（优化批次查询）
# CREATE INDEX idx_settlement_batch_status ON settlement_record(settle_batch, status);
#
# -- id + created_at 复合索引（优化游标分页）
# CREATE INDEX idx_settlement_id_created ON settlement_record(id, created_at);
#
# -- 4. 查看索引创建结果
# SHOW INDEX FROM order_record;
# SHOW INDEX FROM settlement_record;
#
# -- 5. 分析表统计信息（优化查询计划）
# ANALYZE TABLE order_record;
# ANALYZE TABLE settlement_record;
#
# -- 6. 验证索引使用情况
# -- EXPLAIN SELECT * FROM settlement_record WHERE order_id IN (SELECT id FROM order_record WHERE UPPER(sn) = 'TEST');

USE logistics;

-- 1. order_record 表优化
-- SN 查询索引（已有唯一索引 uk_order_sn，无需额外添加）
-- tracking_number 索引（用于关联查询）
-- 如果索引已存在会报错，请忽略或先删除旧索引
CREATE INDEX idx_order_tracking ON order_record(tracking_number);

-- SN 大小写不敏感查询优化（函数索引，注意版本）
-- 【注意】：仅 MySQL 8.0.13+ 支持下面这行语法。
-- 如果你是 MySQL 5.7，请删除下面这行，或者使用虚拟列(Generated Columns)来实现。
CREATE INDEX idx_order_sn_upper ON order_record((UPPER(sn)));

-- 2. settlement_record 表优化 - 基础索引
-- order_id 索引（关键！用于 SN 查询时的 orderId 匹配）
CREATE INDEX idx_settlement_order_id ON settlement_record(order_id);

-- tracking_number 索引（用于运单号查询）
CREATE INDEX idx_settlement_tracking ON settlement_record(tracking_number);

-- order_time 索引（用于排序）
CREATE INDEX idx_settlement_order_time ON settlement_record(order_time);

-- owner_username 索引（用于按归属用户过滤）
CREATE INDEX idx_settlement_owner ON settlement_record(owner_username);

-- 3. settlement_record 表优化 - 复合索引
-- status + order_time 复合索引（用于按状态过滤并排序）
CREATE INDEX idx_settlement_status_time ON settlement_record(status, order_time);

-- status + owner_username 复合索引（常见组合查询）
CREATE INDEX idx_settlement_status_owner ON settlement_record(status, owner_username);

-- tracking_number + model 复合索引（优化关键字查询）
CREATE INDEX idx_settlement_tracking_model ON settlement_record(tracking_number, model);

-- payable_at + status 复合索引（优化日期+状态组合查询）
CREATE INDEX idx_settlement_payable_status ON settlement_record(payable_at, status);

-- settle_batch + status 复合索引（优化批次查询）
CREATE INDEX idx_settlement_batch_status ON settlement_record(settle_batch, status);

-- id + created_at 复合索引（优化游标分页）
CREATE INDEX idx_settlement_id_created ON settlement_record(id, created_at);

-- 4. 查看索引创建结果
SHOW INDEX FROM order_record;
SHOW INDEX FROM settlement_record;

-- 5. 分析表统计信息（优化查询计划）
ANALYZE TABLE order_record;
ANALYZE TABLE settlement_record;

-- 6. 验证索引使用情况
-- EXPLAIN SELECT * FROM settlement_record WHERE order_id IN (SELECT id FROM order_record WHERE UPPER(sn) = 'TEST');