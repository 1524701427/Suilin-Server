# 岁邻后端

岁邻 Python 后端独立仓库。

## 技术栈

- Python 3.12
- FastAPI
- SQLAlchemy 2
- MySQL 8
- Redis
- JWT Bearer Token
- Uvicorn

前端接口协议、MySQL 表结构和主要业务 URL 保持稳定，前端无需因为后端实现变化而重写 API 层。

## 已完成的核心业务

- 家属注册、登录、退出、个人资料。
- 家庭与家庭成员管理、邀请和成员移除。
- 长辈资料、邀请绑定、长辈端独立 clientToken。
- 提醒 CRUD 与长辈完成提醒记录。
- 家庭照护任务 CRUD。
- 健康记录 CRUD。
- 设备查询、绑定和解绑。
- SOS 发起、查询、处理中、关闭。
- 通知偏好、隐私展示偏好。
- 意见反馈。

## 本地启动

准备 Python 3.12、MySQL 8、Redis。

初始化数据库：

```bash
mysql -uroot -p < sql/schema.sql
```

创建虚拟环境并安装依赖：

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Windows PowerShell 激活方式：

```powershell
.venv\Scripts\Activate.ps1
```

复制环境变量示例并按需修改：

```bash
cp .env.example .env
```

启动：

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8080
```

服务地址：`http://127.0.0.1:8080`

Swagger：`http://127.0.0.1:8080/swagger-ui.html`

OpenAPI：`http://127.0.0.1:8080/v3/api-docs`

## Docker

```bash
docker compose up --build
```

## API 分组

- `/api/auth/**`：注册、登录、退出。
- `/api/me`：当前家属资料。
- `/api/families/current/**`：家庭、成员、邀请。
- `/api/family-invites/**`：家庭邀请预览和接受。
- `/api/elders/**`：长辈资料、提醒、健康、设备。
- `/api/elder-invites/**`：长辈邀请和绑定。
- `/api/elder-client/**`：长辈端资料、提醒完成、SOS。
- `/api/care-tasks/**`：照护任务。
- `/api/sos-events/**`：SOS 处理。
- `/api/notification-settings`：通知偏好。
- `/api/privacy-settings`：隐私展示偏好。
- `/api/feedbacks`：意见反馈。

## 兼容说明

响应格式继续使用：

```json
{"code": 0, "message": "ok", "data": {}}
```

家属鉴权继续使用：

```text
Authorization: Bearer <token>
```

数据库使用 `sql/schema.sql` 中的现有表结构，已有 MySQL 数据可以继续使用。密码统一使用 bcrypt。

前端仓库：https://github.com/1524701427/Suilin-Frontend

## 第三方平台能力

微信授权、短信验证码、微信订阅消息、短信/电话 SOS 推送、设备厂商回调、支付和对象存储仍需要对应平台凭证后才能接入。
