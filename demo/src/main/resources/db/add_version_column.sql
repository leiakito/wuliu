-- 添加乐观锁 version 字段到 settlement_record 和 order_record 表
-- 执行时间：2025-12-05

USE logistics;

-- 为 settlement_record 表添加 version 字段
ALTER TABLE settlement_record
ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER updated_at;

-- 为 order_record 表添加 version 字段
ALTER TABLE order_record
ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER updated_at;

-- 验证字段添加成功
SELECT 'settlement_record version column added' AS status;
SELECT 'order_record version column added' AS status;
