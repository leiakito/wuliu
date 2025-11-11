-- Upgrade script to align schema with latest code changes

-- 1. hardware_price category column
ALTER TABLE hardware_price
    ADD COLUMN category VARCHAR(64) NULL AFTER item_name;

-- 2. order_record new columns & constraints
ALTER TABLE order_record
    ADD COLUMN order_time DATETIME NULL AFTER order_date,
    ADD COLUMN imported TINYINT NOT NULL DEFAULT 1 AFTER updated_by;

-- 3. ensure timestamps populated
UPDATE order_record
SET order_time = IFNULL(order_time, CONCAT(order_date, ' 00:00:00')),
    order_date = IFNULL(order_date, DATE(order_time));

-- 4. enforce SN unique
ALTER TABLE order_record
    DROP INDEX tracking_number,
    ADD UNIQUE KEY uk_order_sn (sn);

-- 5. adjust SN column to NOT NULL
ALTER TABLE order_record MODIFY sn VARCHAR(64) NOT NULL;
