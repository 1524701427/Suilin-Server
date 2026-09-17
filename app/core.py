import os
import re
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import redis
from fastapi import Depends, Header, HTTPException
from jose import JWTError, jwt
from passlib.context import CryptContext
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "suilin")
MYSQL_USER = os.getenv("MYSQL_USER", "suilin")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "suilin123")
JWT_SECRET = os.getenv("JWT_SECRET", "dev-only-change-me")
JWT_EXPIRE_DAYS = int(os.getenv("JWT_EXPIRE_DAYS", "30"))

DATABASE_URL = (
    f"mysql+pymysql://{MYSQL_USER}:{MYSQL_PASSWORD}@{MYSQL_HOST}:{MYSQL_PORT}/"
    f"{MYSQL_DATABASE}?charset=utf8mb4"
)
engine = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=1800)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)

redis_client = redis.Redis(
    host=os.getenv("REDIS_HOST", "127.0.0.1"),
    port=int(os.getenv("REDIS_PORT", "6379")),
    password=os.getenv("REDIS_PASSWORD") or None,
    db=0,
    decode_responses=True,
)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


class BusinessError(Exception):
    def __init__(self, message: str, code: int = 400, status_code: int = 200):
        self.message = message
        self.code = code
        self.status_code = status_code
        super().__init__(message)


def ok(data: Any = None):
    return {"code": 0, "message": "ok", "data": data}


def new_id() -> int:
    return uuid.uuid4().int & ((1 << 63) - 1)


def now() -> datetime:
    return datetime.now()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def one(db: Session, sql: str, params: dict | None = None):
    return db.execute(text(sql), params or {}).mappings().first()


def all_rows(db: Session, sql: str, params: dict | None = None):
    return list(db.execute(text(sql), params or {}).mappings().all())


def execute(db: Session, sql: str, params: dict | None = None):
    return db.execute(text(sql), params or {})


def snake_to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def view_row(row):
    if row is None:
        return None
    return {snake_to_camel(k): v for k, v in dict(row).items()}


def create_token(user_id: int) -> str:
    issued = datetime.now(timezone.utc)
    expire = issued + timedelta(days=JWT_EXPIRE_DAYS)
    payload = {
        "sub": str(user_id),
        "iat": int(issued.timestamp()),
        "exp": int(expire.timestamp()),
        "jti": uuid.uuid4().hex,
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


def decode_token(token: str):
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        jti = payload.get("jti")
        if jti:
            try:
                if redis_client.get(f"logout:{jti}"):
                    raise BusinessError("请先登录", 401, 401)
            except redis.RedisError:
                pass
        return payload
    except BusinessError:
        raise
    except JWTError:
        raise BusinessError("请先登录", 401, 401)


def current_user_id(authorization: str | None = Header(default=None)) -> int:
    if not authorization or not authorization.startswith("Bearer "):
        raise BusinessError("请先登录", 401, 401)
    payload = decode_token(authorization[7:].strip())
    try:
        return int(payload["sub"])
    except (KeyError, TypeError, ValueError):
        raise BusinessError("请先登录", 401, 401)


def logout_token(authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        return
    token = authorization[7:].strip()
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        jti = payload.get("jti")
        exp = payload.get("exp")
        if jti and exp:
            ttl = max(1, int(exp - datetime.now(timezone.utc).timestamp()))
            try:
                redis_client.setex(f"logout:{jti}", ttl, "1")
            except redis.RedisError:
                pass
    except JWTError:
        return


def current_family(db: Session, uid: int):
    member = one(db, """
        SELECT * FROM family_members
        WHERE user_id=:uid AND status='ACTIVE'
        ORDER BY created_at DESC LIMIT 1
    """, {"uid": uid})
    if member:
        family = one(db, "SELECT * FROM families WHERE id=:id", {"id": member["family_id"]})
        if family:
            return family

    user = one(db, "SELECT * FROM users WHERE id=:id", {"id": uid})
    if not user:
        raise BusinessError("用户不存在")
    ts = now()
    family_id, member_id = new_id(), new_id()
    family_name = f"{(user['name'] or '我的').strip()}的家庭"
    execute(db, """
        INSERT INTO families(id,name,owner_user_id,created_at,updated_at)
        VALUES(:id,:name,:uid,:created,:updated)
    """, {"id": family_id, "name": family_name, "uid": uid, "created": ts, "updated": ts})
    execute(db, """
        INSERT INTO family_members(id,family_id,user_id,member_role,status,created_at)
        VALUES(:id,:fid,:uid,'OWNER','ACTIVE',:created)
    """, {"id": member_id, "fid": family_id, "uid": uid, "created": ts})
    db.commit()
    return one(db, "SELECT * FROM families WHERE id=:id", {"id": family_id})


def require_member(db: Session, family_id: int, uid: int):
    member = one(db, """
        SELECT * FROM family_members
        WHERE family_id=:fid AND user_id=:uid AND status='ACTIVE'
        LIMIT 1
    """, {"fid": family_id, "uid": uid})
    if not member:
        raise BusinessError("无权访问该家庭")
    return member


def require_elder(db: Session, elder_id: int, uid: int):
    elder = one(db, "SELECT * FROM elders WHERE id=:id", {"id": elder_id})
    if not elder:
        raise BusinessError("长辈不存在")
    if elder["family_id"] is not None:
        require_member(db, elder["family_id"], uid)
    elif int(elder["creator_user_id"]) != uid:
        raise BusinessError("无权访问该长辈")
    return elder


def can_manage(member) -> bool:
    return bool(member and member["member_role"] in ("OWNER", "CAREGIVER"))


def valid_phone(phone: str):
    if not re.fullmatch(r"1\d{10}", phone or ""):
        raise BusinessError("手机号格式不正确")


def verify_password(plain: str, password_hash: str) -> bool:
    try:
        return pwd_context.verify(plain, password_hash)
    except Exception:
        return False
