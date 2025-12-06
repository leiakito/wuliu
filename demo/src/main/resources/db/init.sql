    -- Logistics billing schema & seed data (MySQL 8+)

    -- 创建数据库（如果不存在）
    CREATE DATABASE IF NOT EXISTS logistics CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

    -- 选择数据库
    USE logistics;

    -- 删除旧表（注意依赖顺序）
    SET FOREIGN_KEY_CHECKS=0;
    DROP TABLE IF EXISTS settlement_record;
    DROP TABLE IF EXISTS order_cell_style;
    DROP TABLE IF EXISTS user_submission_log;
    DROP TABLE IF EXISTS user_submission;
    DROP TABLE IF EXISTS hardware_price;
    DROP TABLE IF EXISTS order_record;
    DROP TABLE IF EXISTS sys_log;
    DROP TABLE IF EXISTS sys_user;
    SET FOREIGN_KEY_CHECKS=1;

    -- 用户表
    CREATE TABLE sys_user (
                              id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                              username     VARCHAR(64)     NOT NULL UNIQUE,
                              password     VARCHAR(255)    NOT NULL COMMENT 'BCrypt 密码',
                              role         VARCHAR(32)     NOT NULL,
                              status       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
                              full_name    VARCHAR(64),
                              last_login   DATETIME,
                              created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              deleted      TINYINT         NOT NULL DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 订单表
    CREATE TABLE order_record (
                                  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                  order_date      DATE,
                                  order_time      DATETIME,
                                  tracking_number VARCHAR(64)     NOT NULL,
                                  model           VARCHAR(64),
                                  sn              VARCHAR(64)     NOT NULL,
                                  remark          VARCHAR(255),
                                  category        VARCHAR(64),
                                  status          VARCHAR(32),
                                  paid_at         DATETIME        COMMENT '打款时间',
                                  amount          DECIMAL(15,2),
                                  currency        VARCHAR(16),
                                  weight          DECIMAL(10,2),
                                  customer_name   VARCHAR(64),
                                  created_by      VARCHAR(64),
                                  updated_by      VARCHAR(64),
                                  imported        TINYINT         NOT NULL DEFAULT 1,
                                  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                                  deleted         TINYINT         NOT NULL DEFAULT 0,
                                  -- 已移除唯一约束 uk_order_tracking_sn，允许重复数据
                                  INDEX idx_order_date(order_date),  -- 索引提高查询速度
                                  INDEX idx_order_status(status),    -- 索引提高查询速度
                                  INDEX idx_order_sn(sn),            -- SN 查询索引
                                  INDEX idx_order_tracking(tracking_number), -- 运单号查询索引
                                  FULLTEXT KEY ft_order_keyword (tracking_number, sn, model) -- 全文索引用于关键字搜索
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    CREATE TABLE IF NOT EXISTS order_cell_style (
                                                    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                                    order_id     BIGINT UNSIGNED NULL,     -- 允许为空，避免外键错误
                                                    field        VARCHAR(16)     NOT NULL,
                                                    bg_color     VARCHAR(16),
                                                    font_color   VARCHAR(16),
                                                    strike       TINYINT         NOT NULL DEFAULT 0,
                                                    bold         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否加粗',  -- ⭐ 已包含细体/加粗
                                                    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                    INDEX idx_style_order(order_id),
                                                    UNIQUE KEY uk_style_order_field(order_id, field),
                                                    CONSTRAINT fk_style_order FOREIGN KEY (order_id)
                                                        REFERENCES order_record(id)
                                                        ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    -- 用户提交单号表
    CREATE TABLE user_submission (
                                     id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                     username         VARCHAR(64)     NOT NULL,
                                     owner_username   VARCHAR(64),
                                     tracking_number  VARCHAR(64)     NOT NULL,
                                     status           VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
                                     amount           DECIMAL(15,2),
                                     submission_date  DATE,
                                     remark           VARCHAR(255),
                                     created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     deleted          TINYINT         NOT NULL DEFAULT 0,
                                     UNIQUE KEY uk_submission_tracking_active(tracking_number, deleted),
                                     INDEX idx_submission_status(status),
                                     INDEX idx_submission_date(submission_date),
                                     INDEX idx_submission_owner(owner_username),
                                     INDEX idx_submission_username(username)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 用户提交原文记录表
    CREATE TABLE user_submission_log (
                                        id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                        username    VARCHAR(64)     NOT NULL,
                                        content     TEXT            NOT NULL,
                                        created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        INDEX idx_submission_log_user(username),
                                        INDEX idx_submission_log_created(created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 硬件价格表
    CREATE TABLE hardware_price (
                                    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                    price_date   DATE            NOT NULL,
                                    item_name    VARCHAR(128)    NOT NULL,
                                    price        DECIMAL(15,2)   NULL,
                                    created_by   VARCHAR(64),
                                    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                    deleted      TINYINT         NOT NULL DEFAULT 0,
                                    UNIQUE KEY uk_price_date_item(price_date, item_name),
                                    INDEX idx_price_date(price_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 结账记录表对
    CREATE TABLE settlement_record (
                                       id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                       order_id        BIGINT UNSIGNED,
                                       tracking_number VARCHAR(64)    NOT NULL,
                                       model           VARCHAR(128),
                                       amount          DECIMAL(15,2),
                                       currency        VARCHAR(16),
                                       manual_input    TINYINT         NOT NULL DEFAULT 0,
                                       status          VARCHAR(32),
                                       warning         TINYINT         NOT NULL DEFAULT 0,
                                       settle_batch    VARCHAR(64),
                                       remark          VARCHAR(255),
                                       confirmed_by    VARCHAR(64),
                                       confirmed_at    DATETIME,
                                       owner_username  VARCHAR(64),
                                       submitter_username VARCHAR(64),
                                       order_time      DATETIME,
                                       created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                                       deleted         TINYINT         NOT NULL DEFAULT 0,
                                       INDEX idx_settle_batch(settle_batch),
                                       INDEX idx_settle_status(status),
                                       CONSTRAINT fk_settlement_order FOREIGN KEY (order_id) REFERENCES order_record(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 操作日志表
    CREATE TABLE sys_log (
                             id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                             username   VARCHAR(64),
                             action     VARCHAR(64),
                             detail     VARCHAR(512),
                             ip         VARCHAR(48),
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             INDEX idx_sys_log_username(username)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    -- 初始用户数据（密码: suixiang / operator123，对应 BCrypt 密文）
    INSERT INTO sys_user (username, password, role, status, full_name)
    VALUES
        ('admin', '$2y$10$/wsz6itJhMn8MvHRVarZFurfAsbIRpVlCnfoLTvx0oCeiyaGEakey', 'ADMIN', 'ENABLED', '系统管理员'),
        ('liu', '$2y$10$/wsz6itJhMn8MvHRVarZFurfAsbIRpVlCnfoLTvx0oCeiyaGEakey', 'USER', 'ENABLED', '普通用户'),
    ('tanke', '$2y$10$/wsz6itJhMn8MvHRVarZFurfAsbIRpVlCnfoLTvx0oCeiyaGEakey', 'USER', 'ENABLED', '普通用户');

    # -- 初始订单样例
    # INSERT INTO order_record (order_date, order_time, tracking_number, model, sn, remark, category, status, amount, currency, weight, customer_name, created_by, imported)
    # VALUES
    #     (DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'SF1234567890', 'iPhone 15', 'SN-001', '自动导入样例', '手机', 'UNPAID', 120.00, 'CNY', 0.45, '张三', 'admin', 1),
    #     (DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'YTO9876543210', 'iPad Pro', 'SN-002', '手动录入', '平板', 'NOT_RECEIVED', 215.00, 'CNY', 0.8, '李四', 'operator', 1),
    #     (CURDATE(), NOW(), 'DHL0000000001', 'MacBook Air', 'SN-003', '海外渠道', '电脑', 'PAID', 650.00, 'USD', 1.2, 'ACME HK', 'admin', 1);

#     -- 硬件价格样例
#     INSERT INTO hardware_price (price_date, item_name, price, created_by)
#     VALUES
#         (CURRENT_DATE, 'CPU i9-14900KF', 4099.00, 'admin'),
#         (CURRENT_DATE, 'CPU i7-14700K', 2899.00, 'admin'),
#         (CURRENT_DATE, 'CPU Ryzen 9 7950X', 4499.00, 'admin'),
#         (CURRENT_DATE, 'CPU Ryzen 7 7800X3D', 2999.00, 'admin'),
#         (CURRENT_DATE, '华硕 ROG Z790 HERO', 4999.00, 'admin'),
#         (CURRENT_DATE, '微星 B760M 迫击炮 WIFI', 1299.00, 'admin'),
#         (CURRENT_DATE, '技嘉 B650 AORUS ELITE', 1599.00, 'admin'),
#         (CURRENT_DATE, 'RTX 4070 SUPER', 4499.00, 'admin'),
#         (CURRENT_DATE, 'RTX 4070 Ti SUPER', 5999.00, 'admin'),
#         (CURRENT_DATE, 'RX 7900 XTX', 6999.00, 'admin'),
#         (CURRENT_DATE, '金士顿 32GB DDR5 6000', 799.00, 'admin'),
#         (CURRENT_DATE, '海盗船 32GB DDR5 6000 RGB', 899.00, 'admin'),
#         (CURRENT_DATE, '芝奇 64GB DDR5 6000', 1599.00, 'admin'),
#         (CURRENT_DATE, '三星 990 PRO 1TB', 799.00, 'admin'),
#         (CURRENT_DATE, '西数 SN850X 1TB', 749.00, 'admin'),
#         (CURRENT_DATE, '海康威视 C4000 2TB', 899.00, 'admin');
        # -- 订单对应的结账数据
    # INSERT INTO settlement_record (order_id, tracking_number, model, amount, currency, manual_input, status, warning, settle_batch, payable_at, remark)
    # VALUES
    #     (1, 'SF1234567890', 'iPhone 15', 120.00, 'CNY', 0, 'PENDING', 0, NULL, NULL, '自动待结账'),
    #     (2, 'YTO9876543210', 'iPad Pro', 215.00, 'CNY', 0, 'PENDING', 0, NULL, NULL, '等待财务确认'),
    #     (3, 'DHL0000000001', 'MacBook Air', 650.00, 'USD', 1, 'CONFIRMED', 0, CONCAT('BATCH-', DATE_FORMAT(CURDATE(), '%Y%m%d')), CURDATE(), '人工确认');

    -- 日志样例
    INSERT INTO sys_log (username, action, detail, ip)
    VALUES
        ('admin', 'LOGIN', '管理员登录系统', '127.0.0.1'),
        ('admin', 'IMPORT_ORDER', '批量导入 50 条订单', '127.0.0.1'),
        ('operator', 'GENERATE_SETTLEMENT', '生成 2 条待结账', '127.0.0.1');

