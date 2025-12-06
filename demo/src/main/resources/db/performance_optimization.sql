-- 性能优化与幂等迁移脚本（可多次执行安全）
-- 适用 MySQL 8.0+（函数索引/IF/动态SQL 使用到 8.0 特性）

USE logistics;

-- =============================
-- A. 架构迁移：允许重复数据，移除唯一约束
-- 删除所有唯一约束，允许相同 tracking_number + sn 组合的重复记录
-- 同时补充 sn 普通索引，方便按 SN 查询
-- =============================

-- 0) 安全删除旧唯一键 uk_order_sn（若存在则删除；MySQL 不支持 DROP INDEX IF EXISTS，改用信息_schema判断）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'uk_order_sn'
);
SET @sql := IF(@exists > 0,
  'ALTER TABLE order_record DROP INDEX uk_order_sn',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1) 删除复合唯一键 uk_order_tracking_sn（若存在则删除，允许重复数据）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'uk_order_tracking_sn'
);
SET @sql := IF(@exists > 0,
  'ALTER TABLE order_record DROP INDEX uk_order_tracking_sn',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) SN 普通索引（仅当不存在时创建）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'idx_order_sn'
);
SET @sql := IF(@exists = 0,
  'CREATE INDEX idx_order_sn ON order_record(sn)',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) tracking_number 索引（仅当不存在时创建）
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'idx_order_tracking'
);
SET @sql := IF(@exists = 0,
  'CREATE INDEX idx_order_tracking ON order_record(tracking_number)',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) UPPER(sn) 函数索引（仅当不存在时创建）
-- 注意：仅 MySQL 8.0.13+ 支持函数索引，不支持可忽略失败或删除本段
SET @exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'idx_order_sn_upper'
);
SET @sql := IF(@exists = 0,
  'CREATE INDEX idx_order_sn_upper ON order_record((UPPER(sn)))',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================
-- B. settlement_record 索引（幂等创建）
-- =============================

-- 通用过程：检查后再创建（避免重复）
SET @tbl := 'settlement_record';

-- 基础索引
SET @idx := 'idx_settlement_order_id';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(order_id)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_tracking';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(tracking_number)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_order_time';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(order_time)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_owner';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(owner_username)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 复合索引
SET @idx := 'idx_settlement_status_time';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(status, order_time)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_status_owner';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(status, owner_username)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_tracking_model';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(tracking_number, model)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_batch_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(settle_batch, status)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_settlement_id_created';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(id, created_at)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =================record 全文索引（用于关键字搜索优化）
-- =============================
SET @exists_ft := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND index_name = 'ft_order_keyword'
);
SET @sql_ft := IF(@exists_ft = 0,
  'CREATE FULLTEXT INDEX ft_order_keyword ON order_record(tracking_number, sn, model)',
  'DO 0'
);
PREPARE stmt_ft FROM @sql_ft; EXECUTE stmt_ft; DEALLOCATE PREPARE stmt_ft;

-- =============================
-- C. 刷新统计信息（可选）
-- =============================
ANALYZE TABLE order_record;
ANALYZE TABLE settlement_record;

-- =============================
-- D. 允许 hardware_price.price 为 NULL（幂等）
-- =============================
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'hardware_price'
    AND COLUMN_NAME = 'price'
    AND IS_NULLABLE = 'NO'
);
SET @sql := IF(@need > 0,
  'ALTER TABLE hardware_price MODIFY price DECIMAL(15,2) NULL',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================
-- E. user_submission 表迁移与索引（幂等）
-- 目标：确保存在提交人(username)与归属用户(owner_username)列及相关索引
-- =============================

-- 1) 若无 owner_username 列则新增（可空）
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_submission'
    AND COLUMN_NAME = 'owner_username'
);
SET @sql := IF(@need = 0,
  'ALTER TABLE user_submission ADD COLUMN owner_username VARCHAR(64) NULL AFTER username',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 若无 username 列则新增（先允许为空，后回填，再设为 NOT NULL）
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_submission'
    AND COLUMN_NAME = 'username'
);
SET @sql := IF(@need = 0,
  'ALTER TABLE user_submission ADD COLUMN username VARCHAR(64) NULL AFTER id',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.1) 回填 username（为空的用 owner_username，否则用 "system"）
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_submission'
    AND COLUMN_NAME = 'username'
    AND IS_NULLABLE = 'YES'
);
SET @sql := IF(@need > 0,
  'UPDATE user_submission SET username = COALESCE(NULLIF(TRIM(username), ''''), NULLIF(TRIM(owner_username), ''''), ''system'') WHERE username IS NULL OR TRIM(username) = ''''',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2) 设 username 非空
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_submission'
    AND COLUMN_NAME = 'username'
    AND IS_NULLABLE = 'YES'
);
SET @sql := IF(@need > 0,
  'ALTER TABLE user_submission MODIFY username VARCHAR(64) NOT NULL',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 幂等创建索引
SET @tbl := 'user_submission';

SET @idx := 'idx_submission_status';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(status)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_submission_date';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(submission_date)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_submission_owner';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(owner_username)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := 'idx_submission_username';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(username)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 幂等：移除条件唯一索引，改为普通索引，允许重复 tracking_number
-- 4.1) 删除 uk_submission_tracking_active（若存在）
SET @idx := 'uk_submission_tracking_active';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists > 0, CONCAT('ALTER TABLE ', @tbl, ' DROP INDEX ', @idx), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2) 删除旧的单列唯一索引 uk_submission_tracking（若存在）
SET @idx := 'uk_submission_tracking';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists > 0, CONCAT('ALTER TABLE ', @tbl, ' DROP INDEX ', @idx), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.3) 创建普通索引 idx_submission_tracking（若不存在）
SET @idx := 'idx_submission_tracking';
SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = @tbl AND index_name = @idx);
SET @sql := IF(@exists = 0, CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, '(tracking_number)'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================
-- F. settlement_record 添加 submitter_username 字段（幂等）
-- =============================
SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'settlement_record'
    AND COLUMN_NAME = 'submitter_username'
);
SET @sql := IF(@need = 0,
  'ALTER TABLE settlement_record ADD COLUMN submitter_username VARCHAR(64) AFTER owner_username',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证（按需执行）
-- EXPLAIN SELECT * FROM settlement_record WHERE order_id IN (SELECT id FROM order_record WHERE UPPER(sn) = 'TEST');
-- EXPLAIN SELECT * FROM settlement_record WHERE status = 'PENDING' ORDER BY order_time DESC LIMIT 20;
-- EXPLAIN SELECT * FROM settlement_record WHERE status = 'PENDING' AND owner_username = 'user1' LIMIT 20;
