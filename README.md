# 岁邻后端

岁邻 Java 后端独立仓库。

技术栈：Java 21 + Spring Boot 3.3 + MySQL 8 + Redis + MyBatis-Plus + Sa-Token。
Docker 仅作为可选部署方式，本地调试不要求使用 Docker。

## 已完成的核心业务

- 家属注册、登录、退出、个人资料。
- 家庭 `families` 与家庭成员 `family_members`。
- 家庭成员邀请、手机号校验接受邀请、成员角色与移除成员。
- 家属添加和修改长辈资料，已有旧数据会在首次加载时归入当前家庭。
- 长辈邀请、一键绑定、独立 `clientToken`。
- 提醒 CRUD、长辈完成提醒记录。
- 家庭照护任务 CRUD、负责人、截止时间、完成状态。
- 健康记录查询、新增、修改和删除；健康记录保留数据来源。
- 设备查询、绑定和解绑。
- SOS 事件：长辈发起、家属查询、标记处理中、确认关闭。
- 通知偏好持久化。
- 隐私展示偏好持久化。
- 意见反馈入库。

## 数据原则

- 长辈不注册账号。
- 年龄不入库，只在存在 `birthday` 时动态计算；生日可以不填，不会伪造年龄。
- 长辈资料由家属创建和维护。
- 邀请链接只携带随机 token，不把姓名、生日等个人数据放进 URL。
- 健康数据来源限定为 `FAMILY_MANUAL`、`ELDER_MANUAL`、`DEVICE` 或 `HOSPITAL`。
- 设备在线状态只来自设备数据，不在后端生成假在线状态。
- SOS 当前已经完成数据库事件和家属处理闭环，但不会在未接入微信订阅消息/短信前声称“家属已收到推送”。

## 数据库

全新数据库执行：

```bash
mysql -uroot -p < sql/schema.sql
```

如果数据库是早期 MVP 版本，请先备份，再按 `sql/migration_v2.sql` 的说明升级。

主要业务表：

```text
users
families
family_members
family_invites
elders
elder_invites
reminders
reminder_records
care_tasks
health_records
devices
sos_events
notification_settings
privacy_settings
feedbacks
```

## 本地直接启动（推荐开发方式）

准备：JDK 21、Maven 3.9+、MySQL 8、Redis。

默认配置使用环境变量；可以在 IDEA 的 Run Configuration 中设置数据库和 Redis 参数，也可以直接使用默认的本机地址。

```bash
mvn spring-boot:run
```

或者直接运行：

```text
com.suilin.SuilinApplication
```

服务：`http://127.0.0.1:8080`

Swagger：`http://127.0.0.1:8080/swagger-ui.html`

Docker 仍然保留作为可选方案：

```bash
docker compose up --build
```

## 接口分组

- `/api/auth/**`：注册、登录、退出。
- `/api/me`：当前家属资料。
- `/api/families/current/**`：当前家庭、成员、家庭邀请。
- `/api/family-invites/**`：家庭邀请预览和接受。
- `/api/elders/**`：长辈资料、绑定邀请、提醒、健康、设备。
- `/api/elder-invites/**`：长辈邀请预览和绑定。
- `/api/elder-client/**`：长辈端资料、提醒完成、SOS。
- `/api/care-tasks/**`：家庭照护任务。
- `/api/sos-events/**`：家属端 SOS 处理。
- `/api/notification-settings`：通知偏好。
- `/api/privacy-settings`：隐私展示偏好。
- `/api/feedbacks`：意见反馈。

前端仓库：`https://github.com/1524701427/Suilin-Frontend`

## 仍需第三方平台才能真正完成的能力

以下不是普通 CRUD，不能在没有平台凭证的情况下伪造完成：微信 openid / 微信授权登录、短信验证码和找回密码、微信订阅消息、短信/电话 SOS 实时推送、设备厂商回调、真实服务商/养老机构数据、订单支付和对象存储。
