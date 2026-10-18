-- 创建数据库用户（如果不存在）
CREATE USER IF NOT EXISTS 'restaurant'@'localhost' IDENTIFIED BY 'restaurant';
CREATE USER IF NOT EXISTS 'restaurant'@'%' IDENTIFIED BY 'restaurant';

-- 授予所有权限
GRANT ALL PRIVILEGES ON restaurant.* TO 'restaurant'@'localhost';
GRANT ALL PRIVILEGES ON restaurant.* TO 'restaurant'@'%';

-- 刷新权限
FLUSH PRIVILEGES;

-- 显示用户
SELECT User, Host FROM mysql.user WHERE User = 'restaurant';
