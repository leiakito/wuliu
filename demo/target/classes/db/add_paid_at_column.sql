-- 添加 paid_at 字段（幂等）
USE logistics;

SET @need := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'order_record'
    AND COLUMN_NAME = 'paid_at'
);
SET @sql := IF(@need = 0,
  'ALTER TABLE order_record ADD COLUMN paid_at DATETIME COMMENT ''打款时间'' AFTER status',
  'DO 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
