# 岁邻后端

岁邻 Java 后端独立仓库。

技术栈：Java 21 + Spring Boot 3.3 + MySQL 8 + Redis + MyBatis-Plus + Sa-Token + Docker。

## 当前核心链路

1. 家属注册 / 登录（长辈不注册）。
2. 家属添加长辈资料。
3. 后端生成随机邀请 token。
4. 家属通过微信把邀请发给长辈。
5. 长辈点击邀请并确认一次。
6. 后端把长辈与当前客户端绑定，并签发独立 `clientToken`。
7. 长辈端用 `clientToken` 获取本人资料与提醒，不使用家属登录 token。
8. 长辈完成提醒写入 `reminder_records`。
9. 长辈触发 SOS 写入 `sos_events`。
10. 家属健康页只读取 `health_records`，每条记录都包含数据来源。
11. 设备页只读取 `devices`，不预置在线状态假数据。

## 模块

- `auth`：家属注册、登录、退出。
- `user`：当前家属账号资料。
- `elder`：长辈资料、邀请、一键绑定、长辈端接口。
- `reminder`：提醒创建、查询、长辈完成记录。
- `health`：健康数据与数据来源。
- `device`：长辈设备管理。
- `sos`：紧急求助事件。

## 数据原则

- 年龄不入库，只保存 `birthday`，接口按当前日期计算年龄。
- 长辈资料由家属创建；老人端不注册。
- 邀请链接只携带随机 `inviteToken`，URL 不暴露个人资料。
- 邀请接受后由服务端签发独立 `bound_client_token`。
- 健康数据必须声明来源：`FAMILY_MANUAL`、`ELDER_MANUAL`、`DEVICE` 或 `HOSPITAL`。
- 页面不得凭空生成健康状态、年龄、设备在线状态或提醒完成状态。

## 本地启动

```bash
docker compose up --build
```

服务：`http://localhost:8080`

Swagger：`http://localhost:8080/swagger-ui.html`

前端仓库：`https://github.com/1524701427/Suilin-Frontend`

## 核心接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/me`
- `PUT /api/me`
- `POST /api/elders`
- `GET /api/elders`
- `POST /api/elders/{elderId}/invite`
- `GET /api/elder-invites/{token}`
- `POST /api/elder-invites/{token}/accept`
- `GET /api/elder-client/profile`
- `GET /api/elder-client/reminders`
- `POST /api/elder-client/reminders/{id}/complete`
- `POST /api/elder-client/sos`
- `GET/POST/PUT /api/elders/{elderId}/reminders`
- `GET/POST /api/elders/{elderId}/health-records`
- `GET/POST /api/elders/{elderId}/devices`

微信 openid、手机号验证码、订阅消息、设备厂商回调、SOS 实时推送、对象存储等仍需对应平台凭证后接入。
