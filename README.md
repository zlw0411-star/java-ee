# 饭店点餐系统

系统“选题六”实现，**技术栈为中等档（Nacos 微服务版）**。

## 技术栈

- 后端：Spring Boot 3.2.5 + Spring Cloud Alibaba（Nacos 服务注册/配置）+ OpenFeign（服务间调用）+ JPA
- 数据库：MySQL 8
- 前端：Vue 3 + Element Plus + Vite
- 注册/配置中心：Nacos 2.x（standalone）

| 服务 | 端口 |
|------|------|
| Nacos | 8848 |
| MySQL | 3306 |
| 用户服务 restaurant-user-service | 8081 |
| 订单服务 restaurant-order-service | 8082 |
| 前端 | 5173 |

## 目录结构

```
services/user-service      用户、角色、注册登录、会员等级关联
services/order-service     餐桌、菜品、订单、结算、统计、基础资料维护
frontend                    Vue3 + Element Plus 页面
database/schema.sql         建库、表、约束、视图、存储过程、触发器
database/seed.sql           演示数据（分类/菜品/餐桌/VIP/会员）
.vscode/tasks.json          VSCode 一键启动任务
```

## 运行方式（VSCode 任务）

`Ctrl+Shift+P` → `Tasks: Run Task`，按顺序运行：

1. `1. 启动 MySQL` —— 等出现 `ready for connections`
2. `0. 启动 Nacos` —— 等 <http://localhost:8848/nacos> 能打开（约 20-30 秒）
3. `2. 启动用户服务` —— 等出现 `Started UserServiceApplication`
4. `3. 启动订单服务` —— 等出现 `Started OrderServiceApplication`
5. `4. 启动前端` —— 等出现 `Local: http://localhost:5173/`

MySQL 和 Nacos 就绪后，也可以只运行 `启动全部`（并行启动两个后端 + 前端）。

首次使用需先执行 `database/schema.sql` 和 `database/seed.sql`。

> ⚠️ `.vscode/tasks.json` 中的 JDK / Maven / MySQL / Nacos 路径为**本机绝对路径**
> （`E:\JDK17\...`、`E:\Maven\...`、`E:\MySQL\...`、`E:\Nacos\...`）。换一台机器运行前，
> 请先修改这些路径为本机实际安装位置，或按下方手动命令启动。

> 若要手动启动：`mvn spring-boot:run -pl services/user-service`、
> `mvn spring-boot:run -pl services/order-service`、
> `cd frontend && npm run dev`；Nacos 运行 `bin\startup.cmd -m standalone`。

## 演示账号

| 角色 | 账号 | 密码 | 说明 |
|------|------|------|------|
| 管理员 | admin | Admin123 | 全部功能 + 资料维护 + 报表 |
| 服务员 | waiter | Waiter123 | 选桌代点、加菜、提交、结账 |
| 顾客 | customer | Customer123 | 自助点餐（银卡会员，95 折） |

注册的新账号默认为顾客身份，可在「管理员 → 会员管理」中设置会员等级。

## 角色与功能

### 管理员
- 概览：今日营业额、累计营业额
- 营收报表：选时间段，汇总（订单数/营收合计）+ 明细（订单列表）
- 订单：提交 / 结账
- 用户列表
- 基础资料维护：餐桌增删改、菜品增删改（分类下拉 + 上下架开关）、会员管理（等级增删改 + 给顾客分配等级）

### 服务员
- 选桌（空闲/占用可视化）
- 菜单分类筛选、热销标注
- **会员顾客开关**：打开后选择等级，代点订单自动按等级打折
- 加菜、提交、结账

### 顾客
- 输入桌号自助点餐（自动按本人 VIP 等级打折）
- 加菜、提交订单
- 查看自己的订单与状态

## 下单流程

```
选菜加入订单（未提交 DRAFT）
        │  点【提交订单】
        ▼
    已点好（SUBMITTED）──【结账】──▶ 已结账（SETTLED）
```

- 一个餐桌同一时间只能有一个有效订单（数据库行锁保证）
- 同桌重复下单会自动**合并到现有订单**（不会报“餐桌已被占用”）
- **未提交的订单不能直接结账**，必须先提交
- 结算/取消后自动释放餐桌

## 会员（VIP）折扣

- `vip_level` 表存等级与折扣（普通会员 1.00 / 银卡 0.95 / 金卡 0.90）
- `sys_user.vip_level_id` 关联顾客的会员等级
- 顾客自助下单：后端自动查该顾客等级并打折
- 服务员代点：通过「会员顾客」开关选择等级打折
- 前端显示：顶栏会员等级、购物车折后价、订单应付金额

## 菜品

- 编号为**纯数字**：主食 1xx、热菜 2xx、凉菜 3xx、汤类 4xx、饮品 5xx
- 菜单按**销量前 3** 自动标注「热销」，并排在最前面
- 「删除」仅对未被订单引用过的菜有效；已引用请用「下架」（`enabled=false`），历史订单保留

## 数据库对象

- 约束：主键、外键、唯一、CHECK（状态/价格/折扣范围）、默认值（数量默认 1）、非空
- 索引：`orders(table_id,status)`、`orders(customer_id,created_at)`
- 视图：`v_order_detail`（订单明细）
- 存储过程：`p_revenue_report`（时间段营收）、`p_revenue_daily`（按天营收明细）、`p_hot_dishes`（热门菜品）——均由报表接口通过 `CALL` 实际调用
- 触发器：`trg_order_occupy_table`（下单占桌）、`trg_order_release_table`（结账/取消释放餐桌）

## 安全与架构

### 身份认证
- 登录后签发 **HMAC-SHA256 签名** Token（格式 `base64(payload).签名`），带有效期（默认 12 小时）
- 不知道密钥无法伪造，篡改任一字符即校验失败；过期自动失效
- 两个服务共用同一密钥（`app.token.secret`），可经环境变量 `APP_TOKEN_SECRET` 覆盖

### 接口鉴权
- 业务接口统一校验 `Authorization: Bearer <token>`
- 管理员专属：用户列表/修改、基础资料维护、营收报表
- 顾客只能查看/操作自己的订单

### 服务间调用（Nacos 真正落地）
- 订单服务通过 OpenFeign 按服务名 `restaurant-user-service` 经 Nacos 发现并调用用户服务，
  获取顾客会员折扣（`GET /users/{id}/discount`），不再直接跨库查询 `sys_user`
- 会员等级（`vip_level`）的读写归属 **user-service**（`/vip-levels`），order-service 不再持有该表

## 配置

- 数据库默认账号 `restaurant/restaurant`，可用 `DB_USER`、`DB_PASSWORD`、`DB_HOST`、`NACOS_ADDR` 覆盖
- 后端通过 `spring.config.import: optional:nacos:<服务名>.yml` 从 Nacos 拉取可选配置
