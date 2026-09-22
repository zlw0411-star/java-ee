CREATE DATABASE IF NOT EXISTS restaurant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE restaurant;

CREATE TABLE IF NOT EXISTS vip_level (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(30) NOT NULL UNIQUE,
  discount DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  CONSTRAINT ck_vip_discount CHECK (discount > 0 AND discount <= 1)
);

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(30) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  vip_level_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','WAITER','CUSTOMER')),
  CONSTRAINT fk_user_vip FOREIGN KEY (vip_level_id) REFERENCES vip_level(id)
);

CREATE TABLE IF NOT EXISTS dining_table (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_no VARCHAR(30) NOT NULL UNIQUE,
  table_code VARCHAR(50) NOT NULL UNIQUE,
  seats INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  CONSTRAINT ck_table_seats CHECK (seats > 0),
  CONSTRAINT ck_table_status CHECK (status IN ('AVAILABLE','OCCUPIED'))
);

CREATE TABLE IF NOT EXISTS dish_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS dish (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  category_id BIGINT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  image_url VARCHAR(500),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_dish_category FOREIGN KEY (category_id) REFERENCES dish_category(id),
  CONSTRAINT ck_dish_price CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  table_id BIGINT NOT NULL,
  order_mode VARCHAR(20) NOT NULL,
  creator_id BIGINT NOT NULL,
  customer_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_rate DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  settled_at DATETIME NULL,
  CONSTRAINT fk_order_table FOREIGN KEY (table_id) REFERENCES dining_table(id),
  CONSTRAINT ck_order_mode CHECK (order_mode IN ('WAITER','CUSTOMER')),
  CONSTRAINT ck_order_status CHECK (status IN ('DRAFT','SUBMITTED','SETTLED','CANCELLED')),
  CONSTRAINT ck_order_amount CHECK (total_amount >= 0 AND discount_amount >= 0 AND pay_amount >= 0 AND discount_rate > 0 AND discount_rate <= 1),
  INDEX idx_orders_table_status (table_id, status),
  INDEX idx_orders_customer_created (customer_id, created_at)
);

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  price DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_item_dish FOREIGN KEY (dish_id) REFERENCES dish(id),
  CONSTRAINT ck_item_quantity CHECK (quantity > 0),
  CONSTRAINT ck_item_price CHECK (price >= 0)
);

CREATE OR REPLACE VIEW v_order_detail AS
SELECT o.id, o.order_no, o.order_mode, o.status, o.created_at,
       t.table_no, o.customer_id, o.total_amount, o.discount_amount, o.pay_amount,
       d.name AS dish_name, i.quantity, i.price,
       (i.quantity * i.price) AS line_amount
FROM orders o
JOIN dining_table t ON t.id = o.table_id
JOIN order_item i ON i.order_id = o.id
JOIN dish d ON d.id = i.dish_id;

DROP PROCEDURE IF EXISTS p_revenue_report;
DELIMITER //
CREATE PROCEDURE p_revenue_report(IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT COUNT(*) AS order_count, COALESCE(SUM(pay_amount), 0) AS revenue
  FROM orders
  WHERE status = 'SETTLED' AND DATE(created_at) BETWEEN p_from AND p_to;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS p_hot_dishes;
DELIMITER //
CREATE PROCEDURE p_hot_dishes(IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT d.id, d.name, SUM(i.quantity) AS sold_quantity,
         SUM(i.quantity * i.price) AS sales_amount
  FROM order_item i JOIN orders o ON o.id=i.order_id JOIN dish d ON d.id=i.dish_id
  WHERE o.status='SETTLED' AND DATE(o.created_at) BETWEEN p_from AND p_to
  GROUP BY d.id, d.name ORDER BY sold_quantity DESC;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS p_revenue_daily;
DELIMITER //
CREATE PROCEDURE p_revenue_daily(IN p_from DATE, IN p_to DATE)
BEGIN
  SELECT DATE(created_at) AS biz_date,
         COUNT(*) AS order_count,
         COALESCE(SUM(pay_amount), 0) AS revenue
  FROM orders
  WHERE status = 'SETTLED' AND DATE(created_at) BETWEEN p_from AND p_to
  GROUP BY DATE(created_at)
  ORDER BY biz_date;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS trg_order_occupy_table;
DELIMITER //
CREATE TRIGGER trg_order_occupy_table AFTER INSERT ON orders FOR EACH ROW
BEGIN
  UPDATE dining_table SET status='OCCUPIED' WHERE id=NEW.table_id;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS trg_order_release_table;
DELIMITER //
CREATE TRIGGER trg_order_release_table AFTER UPDATE ON orders FOR EACH ROW
BEGIN
  IF NEW.status IN ('SETTLED','CANCELLED') AND OLD.status NOT IN ('SETTLED','CANCELLED') THEN
    UPDATE dining_table SET status='AVAILABLE' WHERE id=NEW.table_id;
  END IF;
END//
DELIMITER ;
