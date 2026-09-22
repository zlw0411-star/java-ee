SET NAMES utf8mb4;
USE restaurant;
-- 菜品分类
INSERT INTO dish_category(name) VALUES ('主食'),('热菜'),('饮品'),('凉菜'),('汤类') ON DUPLICATE KEY UPDATE name=VALUES(name);

-- VIP等级
INSERT INTO vip_level(name, discount) VALUES ('普通会员', 1.00),('银卡会员', 0.95),('金卡会员', 0.90) ON DUPLICATE KEY UPDATE discount=VALUES(discount);

-- 演示顾客设为银卡会员（95折）
UPDATE sys_user SET vip_level_id=(SELECT id FROM vip_level WHERE name='银卡会员') WHERE username='customer';

-- 餐桌（简单桌号 1-8）
INSERT INTO dining_table(table_no, table_code, seats) VALUES
 ('1','1号桌',2),('2','2号桌',2),('3','3号桌',4),('4','4号桌',4),
 ('5','5号桌',4),('6','6号桌',6),('7','7号桌',6),('8','8号桌',8)
ON DUPLICATE KEY UPDATE table_no=VALUES(table_no);

-- 菜品数据（编号：纯数字，主食1xx 热菜2xx 凉菜3xx 汤类4xx 饮品5xx）
-- 主食
INSERT INTO dish(code,name,category_id,price) SELECT '101','米饭',id,2 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='101');
INSERT INTO dish(code,name,category_id,price) SELECT '102','炒饭',id,15 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='102');
INSERT INTO dish(code,name,category_id,price) SELECT '103','炒面',id,16 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='103');
INSERT INTO dish(code,name,category_id,price) SELECT '104','水饺',id,18 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='104');
INSERT INTO dish(code,name,category_id,price) SELECT '105','牛肉面',id,22 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='105');
INSERT INTO dish(code,name,category_id,price) SELECT '106','阳春面',id,12 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='106');
INSERT INTO dish(code,name,category_id,price) SELECT '107','蛋炒饭',id,16 FROM dish_category WHERE name='主食' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='107');

-- 热菜
INSERT INTO dish(code,name,category_id,price) SELECT '201','宫保鸡丁',id,38 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='201');
INSERT INTO dish(code,name,category_id,price) SELECT '202','鱼香肉丝',id,35 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='202');
INSERT INTO dish(code,name,category_id,price) SELECT '203','红烧肉',id,48 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='203');
INSERT INTO dish(code,name,category_id,price) SELECT '204','糖醋里脊',id,42 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='204');
INSERT INTO dish(code,name,category_id,price) SELECT '205','麻婆豆腐',id,22 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='205');
INSERT INTO dish(code,name,category_id,price) SELECT '206','回锅肉',id,36 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='206');
INSERT INTO dish(code,name,category_id,price) SELECT '207','水煮鱼',id,58 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='207');
INSERT INTO dish(code,name,category_id,price) SELECT '208','辣子鸡',id,45 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='208');
INSERT INTO dish(code,name,category_id,price) SELECT '209','京酱肉丝',id,34 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='209');
INSERT INTO dish(code,name,category_id,price) SELECT '210','酸辣土豆丝',id,16 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='210');
INSERT INTO dish(code,name,category_id,price) SELECT '211','蒜蓉西兰花',id,20 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='211');
INSERT INTO dish(code,name,category_id,price) SELECT '212','番茄炒蛋',id,18 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='212');
INSERT INTO dish(code,name,category_id,price) SELECT '213','干煸四季豆',id,22 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='213');
INSERT INTO dish(code,name,category_id,price) SELECT '214','铁板牛肉',id,46 FROM dish_category WHERE name='热菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='214');

-- 凉菜
INSERT INTO dish(code,name,category_id,price) SELECT '301','凉拌黄瓜',id,12 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='301');
INSERT INTO dish(code,name,category_id,price) SELECT '302','皮蛋豆腐',id,18 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='302');
INSERT INTO dish(code,name,category_id,price) SELECT '303','口水鸡',id,28 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='303');
INSERT INTO dish(code,name,category_id,price) SELECT '304','凉拌木耳',id,14 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='304');
INSERT INTO dish(code,name,category_id,price) SELECT '305','夫妻肺片',id,38 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='305');
INSERT INTO dish(code,name,category_id,price) SELECT '306','蒜泥白肉',id,30 FROM dish_category WHERE name='凉菜' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='306');

-- 汤类
INSERT INTO dish(code,name,category_id,price) SELECT '401','西红柿鸡蛋汤',id,15 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='401');
INSERT INTO dish(code,name,category_id,price) SELECT '402','紫菜蛋花汤',id,12 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='402');
INSERT INTO dish(code,name,category_id,price) SELECT '403','酸辣汤',id,18 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='403');
INSERT INTO dish(code,name,category_id,price) SELECT '404','冬瓜排骨汤',id,28 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='404');
INSERT INTO dish(code,name,category_id,price) SELECT '405','玉米排骨汤',id,32 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='405');
INSERT INTO dish(code,name,category_id,price) SELECT '406','番茄牛腩汤',id,36 FROM dish_category WHERE name='汤类' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='406');

-- 饮品
INSERT INTO dish(code,name,category_id,price) SELECT '501','可乐',id,5 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='501');
INSERT INTO dish(code,name,category_id,price) SELECT '502','雪碧',id,5 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='502');
INSERT INTO dish(code,name,category_id,price) SELECT '503','橙汁',id,8 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='503');
INSERT INTO dish(code,name,category_id,price) SELECT '504','柠檬茶',id,10 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='504');
INSERT INTO dish(code,name,category_id,price) SELECT '505','酸梅汤',id,8 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='505');
INSERT INTO dish(code,name,category_id,price) SELECT '506','珍珠奶茶',id,12 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='506');
INSERT INTO dish(code,name,category_id,price) SELECT '507','鲜榨西瓜汁',id,12 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='507');
INSERT INTO dish(code,name,category_id,price) SELECT '508','茉莉花茶',id,8 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='508');
INSERT INTO dish(code,name,category_id,price) SELECT '509','王老吉',id,6 FROM dish_category WHERE name='饮品' AND NOT EXISTS(SELECT 1 FROM dish WHERE code='509');
