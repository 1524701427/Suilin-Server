"""FastAPI application exposing the Suilin family-care REST API."""

import json
import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Optional

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from .core import (
    BusinessError, all_rows, can_manage, create_token, current_family, current_user_id,
    execute, get_db, logout_token, new_id, now, ok, one, pwd_context, require_elder,
    require_member, valid_phone, verify_password, view_row
)

# Application and exception handling
app = FastAPI(title="岁邻 API", version="2.0.0", docs_url="/swagger-ui.html", openapi_url="/v3/api-docs")


@app.exception_handler(BusinessError)
async def business_error_handler(_: Request, exc: BusinessError):
    """Convert a business exception into the legacy API response format."""
    return JSONResponse(status_code=exc.status_code, content={"code": exc.code, "message": exc.message, "data": None})


@app.exception_handler(RequestValidationError)
async def validation_error_handler(_: Request, exc: RequestValidationError):
    """Convert request validation failures into a client-friendly response."""
    detail = exc.errors()[0] if exc.errors() else {}
    message = detail.get("msg", "参数错误")
    return JSONResponse(status_code=200, content={"code": 400, "message": message, "data": None})


@app.exception_handler(Exception)
async def generic_error_handler(_: Request, exc: Exception):
    """Hide unexpected exception details behind a generic server error."""
    return JSONResponse(status_code=500, content={"code": 500, "message": "服务器内部错误", "data": None})


@app.get("/health")
def health():
    """Return a lightweight process health response."""
    return {"status": "ok"}


# Authentication and current-user APIs
class RegisterRequest(BaseModel):
    """Validate the payload for register operations."""
    phone: str
    name: str = Field(min_length=1, max_length=50)
    password: str = Field(min_length=6, max_length=64)


class LoginRequest(BaseModel):
    """Validate the payload for login operations."""
    phone: str
    password: str


@app.post("/api/auth/register")
def register(req: RegisterRequest, db: Session = Depends(get_db)):
    """Register a family user and return an access token."""
    valid_phone(req.phone)
    if one(db, "SELECT id FROM users WHERE phone=:phone", {"phone": req.phone}):
        raise BusinessError("手机号已注册")
    uid, ts = new_id(), now()
    execute(db, """
        INSERT INTO users(id,phone,name,password_hash,role,status,created_at,updated_at)
        VALUES(:id,:phone,:name,:password,'FAMILY','ACTIVE',:created,:updated)
    """, {
        "id": uid, "phone": req.phone, "name": req.name.strip(),
        "password": pwd_context.hash(req.password), "created": ts, "updated": ts,
    })
    db.commit()
    return ok({"userId": uid, "token": create_token(uid), "role": "FAMILY"})


@app.post("/api/auth/login")
def login(req: LoginRequest, db: Session = Depends(get_db)):
    """Authenticate a family user and return an access token."""
    valid_phone(req.phone)
    user = one(db, "SELECT * FROM users WHERE phone=:phone", {"phone": req.phone})
    if not user or not verify_password(req.password, user["password_hash"]):
        raise BusinessError("手机号或密码错误")
    if user["status"] != "ACTIVE":
        raise BusinessError("账号不可用")
    return ok({"userId": user["id"], "token": create_token(user["id"]), "name": user["name"], "role": user["role"]})


@app.post("/api/auth/logout")
def logout(authorization: str | None = Header(default=None)):
    """Invalidate the current access token when possible."""
    logout_token(authorization)
    return ok()


class MeUpdate(BaseModel):
    """Validate the payload for me update operations."""
    name: str = Field(min_length=1, max_length=50)


@app.get("/api/me")
def me(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Return the authenticated family user's profile."""
    user = one(db, "SELECT * FROM users WHERE id=:id", {"id": uid})
    if not user:
        raise BusinessError("用户不存在")
    return ok({"id": user["id"], "name": user["name"], "phone": user["phone"], "role": user["role"]})


@app.put("/api/me")
def update_me(req: MeUpdate, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update the authenticated family user's display name."""
    execute(db, "UPDATE users SET name=:name,updated_at=:ts WHERE id=:id", {"name": req.name.strip(), "ts": now(), "id": uid})
    db.commit()
    return me(uid, db)


def family_view(db: Session, family):
    """Serialize a family database row for API output."""
    return {"id": family["id"], "name": family["name"], "ownerUserId": family["owner_user_id"]}


@app.get("/api/families/current")
def get_current_family(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Return the authenticated user's active family."""
    return ok(family_view(db, current_family(db, uid)))


@app.get("/api/families/current/members")
def get_family_members(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List active members of the current family."""
    family = current_family(db, uid)
    rows = all_rows(db, """
        SELECT fm.id,fm.family_id,fm.user_id,fm.member_role,fm.status,fm.created_at,
               u.name,u.phone
        FROM family_members fm JOIN users u ON u.id=fm.user_id
        WHERE fm.family_id=:fid AND fm.status='ACTIVE'
        ORDER BY fm.created_at
    """, {"fid": family["id"]})
    return ok([{
        "id": r["id"], "familyId": r["family_id"], "userId": r["user_id"],
        "memberRole": r["member_role"], "status": r["status"],
        "name": r["name"], "phone": r["phone"], "createdAt": r["created_at"],
    } for r in rows])


# Family membership and invitations
class FamilyInviteRequest(BaseModel):
    """Validate the payload for family invite operations."""
    phone: str
    memberRole: str = "MEMBER"


@app.post("/api/families/current/invites")
def create_family_invite(req: FamilyInviteRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create a time-limited invitation for a family member."""
    valid_phone(req.phone)
    family = current_family(db, uid)
    member = require_member(db, family["id"], uid)
    if not can_manage(member):
        raise BusinessError("无权邀请家庭成员")
    role = req.memberRole if req.memberRole in ("MEMBER", "CAREGIVER") else "MEMBER"
    token, iid, ts = uuid.uuid4().hex, new_id(), now()
    execute(db, """
        INSERT INTO family_invites(id,family_id,inviter_user_id,invite_phone,member_role,invite_token,status,expires_at,created_at)
        VALUES(:id,:fid,:uid,:phone,:role,:token,'WAITING',:expires,:created)
    """, {"id": iid, "fid": family["id"], "uid": uid, "phone": req.phone, "role": role,
          "token": token, "expires": ts + timedelta(days=7), "created": ts})
    db.commit()
    return ok({"id": iid, "token": token, "inviteToken": token, "expiresAt": ts + timedelta(days=7)})


@app.get("/api/family-invites/{token}")
def family_invite_preview(token: str, db: Session = Depends(get_db)):
    """Return public preview information for a family invitation."""
    inv = one(db, """
        SELECT fi.*, f.name family_name, u.name inviter_name
        FROM family_invites fi
        JOIN families f ON f.id=fi.family_id
        JOIN users u ON u.id=fi.inviter_user_id
        WHERE fi.invite_token=:token
    """, {"token": token})
    if not inv:
        raise BusinessError("邀请不存在")
    return ok({
        "familyName": inv["family_name"], "inviterName": inv["inviter_name"],
        "invitePhone": inv["invite_phone"], "memberRole": inv["member_role"],
        "status": inv["status"], "expiresAt": inv["expires_at"],
    })


@app.post("/api/family-invites/{token}/accept")
def accept_family_invite(token: str, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Accept a family invitation for the authenticated user."""
    inv = one(db, "SELECT * FROM family_invites WHERE invite_token=:token", {"token": token})
    if not inv:
        raise BusinessError("邀请不存在")
    if inv["status"] != "WAITING" or inv["expires_at"] < now():
        raise BusinessError("邀请已失效")
    user = one(db, "SELECT * FROM users WHERE id=:id", {"id": uid})
    if not user or user["phone"] != inv["invite_phone"]:
        raise BusinessError("当前账号手机号与邀请手机号不一致")
    existing = one(db, "SELECT id FROM family_members WHERE family_id=:fid AND user_id=:uid", {"fid": inv["family_id"], "uid": uid})
    if not existing:
        execute(db, """
            INSERT INTO family_members(id,family_id,user_id,member_role,status,created_at)
            VALUES(:id,:fid,:uid,:role,'ACTIVE',:ts)
        """, {"id": new_id(), "fid": inv["family_id"], "uid": uid, "role": inv["member_role"], "ts": now()})
    else:
        execute(db, "UPDATE family_members SET status='ACTIVE',member_role=:role WHERE id=:id", {"role": inv["member_role"], "id": existing["id"]})
    execute(db, """
        UPDATE family_invites SET status='ACCEPTED',accepted_by_user_id=:uid,accepted_at=:ts
        WHERE id=:id
    """, {"uid": uid, "ts": now(), "id": inv["id"]})
    db.commit()
    return ok({"familyId": inv["family_id"]})


@app.delete("/api/families/current/members/{member_id}")
def remove_family_member(member_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Remove a non-owner member from the current family."""
    family = current_family(db, uid)
    me_member = require_member(db, family["id"], uid)
    if not can_manage(me_member):
        raise BusinessError("无权移除家庭成员")
    target = one(db, "SELECT * FROM family_members WHERE id=:id AND family_id=:fid", {"id": member_id, "fid": family["id"]})
    if not target:
        raise BusinessError("家庭成员不存在")
    if target["member_role"] == "OWNER":
        raise BusinessError("不能移除家庭创建者")
    execute(db, "UPDATE family_members SET status='REMOVED' WHERE id=:id", {"id": member_id})
    db.commit()
    return ok()


# Elder profiles and elder-client binding
class ElderRequest(BaseModel):
    """Validate the payload for elder operations."""
    name: str
    relation: str
    birthday: Optional[date] = None
    phone: Optional[str] = None
    healthTags: list[str] = Field(default_factory=list)


def elder_view(row):
    """Serialize an elder row while hiding private client credentials."""
    data = view_row(row)
    raw = row["health_tags_json"] if row and "health_tags_json" in row else None
    if raw:
        try:
            data["healthTags"] = json.loads(raw) if isinstance(raw, str) else raw
        except Exception:
            data["healthTags"] = []
    else:
        data["healthTags"] = []
    if row and row["birthday"]:
        today = date.today()
        b = row["birthday"]
        data["age"] = today.year - b.year - ((today.month, today.day) < (b.month, b.day))
    else:
        data["age"] = None
    data.pop("healthTagsJson", None)
    data.pop("boundClientToken", None)
    return data


@app.post("/api/elders")
def create_elder(req: ElderRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create an elder profile in the current family."""
    family = current_family(db, uid)
    eid, ts = new_id(), now()
    execute(db, """
        INSERT INTO elders(id,family_id,creator_user_id,name,relation,birthday,phone,health_tags_json,bind_status,created_at,updated_at)
        VALUES(:id,:fid,:uid,:name,:relation,:birthday,:phone,:tags,'WAITING',:created,:updated)
    """, {"id": eid, "fid": family["id"], "uid": uid, "name": req.name.strip(), "relation": req.relation.strip(),
          "birthday": req.birthday, "phone": req.phone, "tags": json.dumps(req.healthTags, ensure_ascii=False),
          "created": ts, "updated": ts})
    db.commit()
    return ok(elder_view(one(db, "SELECT * FROM elders WHERE id=:id", {"id": eid})))


@app.get("/api/elders")
def list_elders(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List elders belonging to the current family."""
    family = current_family(db, uid)
    rows = all_rows(db, "SELECT * FROM elders WHERE family_id=:fid ORDER BY created_at DESC", {"fid": family["id"]})
    return ok([elder_view(r) for r in rows])


@app.put("/api/elders/{elder_id}")
def update_elder(elder_id: int, req: ElderRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update an elder profile accessible to the current user."""
    require_elder(db, elder_id, uid)
    execute(db, """
        UPDATE elders SET name=:name,relation=:relation,birthday=:birthday,phone=:phone,
        health_tags_json=:tags,updated_at=:updated WHERE id=:id
    """, {"name": req.name.strip(), "relation": req.relation.strip(), "birthday": req.birthday, "phone": req.phone,
          "tags": json.dumps(req.healthTags, ensure_ascii=False), "updated": now(), "id": elder_id})
    db.commit()
    return ok(elder_view(one(db, "SELECT * FROM elders WHERE id=:id", {"id": elder_id})))


@app.post("/api/elders/{elder_id}/invite")
def create_elder_invite(elder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create a time-limited elder-client binding invitation."""
    require_elder(db, elder_id, uid)
    token, iid, ts = uuid.uuid4().hex, new_id(), now()
    execute(db, """
        INSERT INTO elder_invites(id,elder_id,inviter_user_id,invite_token,status,expires_at,created_at)
        VALUES(:id,:eid,:uid,:token,'WAITING',:expires,:created)
    """, {"id": iid, "eid": elder_id, "uid": uid, "token": token, "expires": ts + timedelta(days=7), "created": ts})
    db.commit()
    return ok({"id": iid, "token": token, "inviteToken": token, "expiresAt": ts + timedelta(days=7)})


@app.post("/api/elders/{elder_id}/unbind")
def unbind_elder(elder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Remove an elder-client binding and invalidate its client token."""
    require_elder(db, elder_id, uid)
    execute(db, """
        UPDATE elders SET bind_status='WAITING',bound_client_id=NULL,bound_client_token=NULL,bound_at=NULL,updated_at=:ts
        WHERE id=:id
    """, {"ts": now(), "id": elder_id})
    db.commit()
    return ok()


@app.get("/api/elder-invites/{token}")
def elder_invite_preview(token: str, db: Session = Depends(get_db)):
    """Return public preview information for an elder binding invitation."""
    inv = one(db, """
        SELECT ei.*, e.name elder_name,e.relation,e.bind_status,u.name inviter_name
        FROM elder_invites ei JOIN elders e ON e.id=ei.elder_id JOIN users u ON u.id=ei.inviter_user_id
        WHERE ei.invite_token=:token
    """, {"token": token})
    if not inv:
        raise BusinessError("邀请不存在")
    return ok({"elderId": inv["elder_id"], "elderName": inv["elder_name"], "relation": inv["relation"],
               "bindStatus": inv["bind_status"], "inviterName": inv["inviter_name"],
               "status": inv["status"], "expiresAt": inv["expires_at"]})


class BindRequest(BaseModel):
    """Validate the payload for bind operations."""
    clientId: str


@app.post("/api/elder-invites/{token}/accept")
def accept_elder_invite(token: str, req: BindRequest, db: Session = Depends(get_db)):
    """Bind an elder client and issue its independent client token."""
    inv = one(db, "SELECT * FROM elder_invites WHERE invite_token=:token", {"token": token})
    if not inv:
        raise BusinessError("邀请不存在")
    if inv["status"] != "WAITING" or inv["expires_at"] < now():
        raise BusinessError("邀请已失效")
    client_token = uuid.uuid4().hex + uuid.uuid4().hex
    execute(db, """
        UPDATE elders SET bind_status='BOUND',bound_client_id=:cid,bound_client_token=:ct,bound_at=:ts,updated_at=:ts
        WHERE id=:id
    """, {"cid": req.clientId.strip(), "ct": client_token, "ts": now(), "id": inv["elder_id"]})
    execute(db, "UPDATE elder_invites SET status='ACCEPTED',accepted_at=:ts WHERE id=:id", {"ts": now(), "id": inv["id"]})
    db.commit()
    return ok({"elderId": inv["elder_id"], "clientToken": client_token})


def require_elder_client(db: Session, client_token: str):
    """Resolve an elder from a valid bound client token."""
    elder = one(db, "SELECT * FROM elders WHERE bound_client_token=:token AND bind_status='BOUND'", {"token": client_token})
    if not elder:
        raise BusinessError("长辈端绑定无效", 401, 401)
    return elder


@app.get("/api/elder-client/profile")
def elder_client_profile(clientToken: str, db: Session = Depends(get_db)):
    """Return the elder profile available to the bound elder client."""
    return ok(elder_view(require_elder_client(db, clientToken)))


class ClientRequest(BaseModel):
    """Validate the payload for client operations."""
    clientToken: str


class SosRequest(BaseModel):
    """Validate the payload for sos operations."""
    clientToken: str
    latitude: Optional[Decimal] = None
    longitude: Optional[Decimal] = None


@app.get("/api/elder-client/reminders")
def elder_client_reminders(clientToken: str, db: Session = Depends(get_db)):
    """List enabled reminders for the bound elder client."""
    elder = require_elder_client(db, clientToken)
    rows = all_rows(db, "SELECT * FROM reminders WHERE elder_id=:eid AND enabled=1 ORDER BY schedule_time", {"eid": elder["id"]})
    return ok([view_row(r) for r in rows])


@app.post("/api/elder-client/reminders/{reminder_id}/complete")
def complete_elder_reminder(reminder_id: int, req: ClientRequest, db: Session = Depends(get_db)):
    """Record completion of a reminder by the elder client."""
    elder = require_elder_client(db, req.clientToken)
    reminder = one(db, "SELECT * FROM reminders WHERE id=:id AND elder_id=:eid", {"id": reminder_id, "eid": elder["id"]})
    if not reminder:
        raise BusinessError("提醒不存在")
    ts = now()
    execute(db, """
        INSERT INTO reminder_records(id,reminder_id,elder_id,scheduled_at,status,completed_at,source_type,created_at)
        VALUES(:id,:rid,:eid,:scheduled,'DONE',:completed,'ELDER_ACTION',:created)
    """, {"id": new_id(), "rid": reminder_id, "eid": elder["id"], "scheduled": ts, "completed": ts, "created": ts})
    db.commit()
    return ok()


@app.post("/api/elder-client/sos")
def elder_client_sos(req: SosRequest, db: Session = Depends(get_db)):
    """Create an SOS event from the bound elder client."""
    elder = require_elder_client(db, req.clientToken)
    sid, ts = new_id(), now()
    execute(db, """
        INSERT INTO sos_events(id,elder_id,source_type,source_ref,latitude,longitude,status,created_at)
        VALUES(:id,:eid,'ELDER_CLIENT',:ref,:lat,:lng,'OPEN',:ts)
    """, {"id": sid, "eid": elder["id"], "ref": elder["bound_client_id"], "lat": req.latitude, "lng": req.longitude, "ts": ts})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM sos_events WHERE id=:id", {"id": sid})))


# Reminder APIs
class ReminderRequest(BaseModel):
    """Validate the payload for reminder operations."""
    title: str
    type: str
    scheduleTime: str
    dosage: Optional[str] = None
    repeatRule: Optional[str] = None
    notifyAfterMinutes: Optional[int] = None
    enabled: bool = True


@app.get("/api/elders/{elder_id}/reminders")
def list_reminders(elder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List reminders for an elder accessible to the current family user."""
    require_elder(db, elder_id, uid)
    return ok([view_row(r) for r in all_rows(db, "SELECT * FROM reminders WHERE elder_id=:eid ORDER BY created_at DESC", {"eid": elder_id})])


@app.post("/api/elders/{elder_id}/reminders")
def create_reminder(elder_id: int, req: ReminderRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create a reminder for an elder."""
    require_elder(db, elder_id, uid)
    rid, ts = new_id(), now()
    execute(db, """
        INSERT INTO reminders(id,elder_id,created_by_user_id,title,type,schedule_time,dosage,repeat_rule,notify_after_minutes,enabled,created_at,updated_at)
        VALUES(:id,:eid,:uid,:title,:type,:time,:dosage,:repeat,:notify,:enabled,:created,:updated)
    """, {"id": rid, "eid": elder_id, "uid": uid, "title": req.title.strip(), "type": req.type.strip(),
          "time": req.scheduleTime, "dosage": req.dosage, "repeat": req.repeatRule, "notify": req.notifyAfterMinutes,
          "enabled": req.enabled, "created": ts, "updated": ts})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM reminders WHERE id=:id", {"id": rid})))


@app.put("/api/elders/{elder_id}/reminders/{reminder_id}")
def update_reminder(elder_id: int, reminder_id: int, req: ReminderRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update an existing elder reminder."""
    require_elder(db, elder_id, uid)
    if not one(db, "SELECT id FROM reminders WHERE id=:id AND elder_id=:eid", {"id": reminder_id, "eid": elder_id}):
        raise BusinessError("提醒不存在")
    execute(db, """
        UPDATE reminders SET title=:title,type=:type,schedule_time=:time,dosage=:dosage,repeat_rule=:repeat,
        notify_after_minutes=:notify,enabled=:enabled,updated_at=:updated WHERE id=:id
    """, {"title": req.title.strip(), "type": req.type.strip(), "time": req.scheduleTime, "dosage": req.dosage,
          "repeat": req.repeatRule, "notify": req.notifyAfterMinutes, "enabled": req.enabled, "updated": now(), "id": reminder_id})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM reminders WHERE id=:id", {"id": reminder_id})))


@app.delete("/api/elders/{elder_id}/reminders/{reminder_id}")
def delete_reminder(elder_id: int, reminder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Delete an elder reminder."""
    require_elder(db, elder_id, uid)
    execute(db, "DELETE FROM reminders WHERE id=:id AND elder_id=:eid", {"id": reminder_id, "eid": elder_id})
    db.commit()
    return ok()


# Health record APIs
class HealthRequest(BaseModel):
    """Validate the payload for health operations."""
    metricType: str
    valueText: str
    unit: Optional[str] = None
    sourceType: str = "FAMILY_MANUAL"
    sourceRef: Optional[str] = None
    measuredAt: Optional[datetime] = None


@app.get("/api/elders/{elder_id}/health-records")
def list_health(elder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List health records for an elder."""
    require_elder(db, elder_id, uid)
    return ok([view_row(r) for r in all_rows(db, "SELECT * FROM health_records WHERE elder_id=:eid ORDER BY measured_at DESC", {"eid": elder_id})])


@app.post("/api/elders/{elder_id}/health-records")
def create_health(elder_id: int, req: HealthRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create a health record for an elder."""
    require_elder(db, elder_id, uid)
    source = req.sourceType if req.sourceType in ("FAMILY_MANUAL","ELDER_MANUAL","DEVICE","HOSPITAL") else "FAMILY_MANUAL"
    hid, ts = new_id(), now()
    execute(db, """
        INSERT INTO health_records(id,elder_id,metric_type,value_text,unit,source_type,source_ref,recorded_by_user_id,measured_at,created_at)
        VALUES(:id,:eid,:metric,:value,:unit,:source,:ref,:uid,:measured,:created)
    """, {"id": hid, "eid": elder_id, "metric": req.metricType.strip(), "value": req.valueText.strip(),
          "unit": req.unit, "source": source, "ref": req.sourceRef, "uid": uid, "measured": req.measuredAt or ts, "created": ts})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM health_records WHERE id=:id", {"id": hid})))


@app.put("/api/elders/{elder_id}/health-records/{record_id}")
def update_health(elder_id: int, record_id: int, req: HealthRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update an existing health record."""
    require_elder(db, elder_id, uid)
    source = req.sourceType if req.sourceType in ("FAMILY_MANUAL","ELDER_MANUAL","DEVICE","HOSPITAL") else "FAMILY_MANUAL"
    execute(db, """
        UPDATE health_records SET metric_type=:metric,value_text=:value,unit=:unit,source_type=:source,source_ref=:ref,
        measured_at=:measured WHERE id=:id AND elder_id=:eid
    """, {"metric": req.metricType.strip(), "value": req.valueText.strip(), "unit": req.unit, "source": source,
          "ref": req.sourceRef, "measured": req.measuredAt or now(), "id": record_id, "eid": elder_id})
    db.commit()
    row = one(db, "SELECT * FROM health_records WHERE id=:id AND elder_id=:eid", {"id": record_id, "eid": elder_id})
    if not row:
        raise BusinessError("健康记录不存在")
    return ok(view_row(row))


@app.delete("/api/elders/{elder_id}/health-records/{record_id}")
def delete_health(elder_id: int, record_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Delete an elder health record."""
    require_elder(db, elder_id, uid)
    execute(db, "DELETE FROM health_records WHERE id=:id AND elder_id=:eid", {"id": record_id, "eid": elder_id})
    db.commit()
    return ok()


# Device binding APIs
class DeviceRequest(BaseModel):
    """Validate the payload for device operations."""
    deviceType: str
    deviceSn: str


@app.get("/api/elders/{elder_id}/devices")
def list_devices(elder_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List devices bound to an elder."""
    require_elder(db, elder_id, uid)
    return ok([view_row(r) for r in all_rows(db, "SELECT * FROM devices WHERE elder_id=:eid ORDER BY created_at DESC", {"eid": elder_id})])


@app.post("/api/elders/{elder_id}/devices")
def create_device(elder_id: int, req: DeviceRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Bind a device serial number to an elder."""
    require_elder(db, elder_id, uid)
    if one(db, "SELECT id FROM devices WHERE device_sn=:sn", {"sn": req.deviceSn.strip()}):
        raise BusinessError("设备码已被绑定")
    did = new_id()
    execute(db, """
        INSERT INTO devices(id,elder_id,device_type,device_sn,status,created_at)
        VALUES(:id,:eid,:type,:sn,'OFFLINE',:ts)
    """, {"id": did, "eid": elder_id, "type": req.deviceType.strip(), "sn": req.deviceSn.strip(), "ts": now()})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM devices WHERE id=:id", {"id": did})))


@app.delete("/api/elders/{elder_id}/devices/{device_id}")
def delete_device(elder_id: int, device_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Unbind a device from an elder."""
    require_elder(db, elder_id, uid)
    row = one(db, "SELECT * FROM devices WHERE id=:id AND elder_id=:eid", {"id": device_id, "eid": elder_id})
    if not row:
        raise BusinessError("设备不存在")
    execute(db, "DELETE FROM devices WHERE id=:id", {"id": device_id})
    db.commit()
    return ok()


# Family care task APIs
class TaskRequest(BaseModel):
    """Validate the payload for task operations."""
    title: str
    elderId: Optional[int] = None
    assigneeUserId: Optional[int] = None
    dueAt: Optional[datetime] = None
    note: Optional[str] = None
    status: str = "TODO"


def task_view(db: Session, row):
    """Serialize a care task and include the assignee display name."""
    assignee = one(db, "SELECT name FROM users WHERE id=:id", {"id": row["assignee_user_id"]}) if row["assignee_user_id"] else None
    data = view_row(row)
    data["assigneeName"] = assignee["name"] if assignee else ""
    return data


def validate_task_request(db: Session, family_id: int, req: TaskRequest, uid: int):
    """Validate task status, assignee membership, and elder access."""
    if req.status not in ("TODO","DOING","DONE"):
        raise BusinessError("无效的任务状态")
    if req.assigneeUserId:
        m = one(db, "SELECT id FROM family_members WHERE family_id=:fid AND user_id=:uid AND status='ACTIVE'",
                {"fid": family_id, "uid": req.assigneeUserId})
        if not m:
            raise BusinessError("负责人必须是当前家庭成员")
    if req.elderId:
        require_elder(db, req.elderId, uid)


@app.get("/api/care-tasks")
def list_tasks(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List care tasks for the current family."""
    family = current_family(db, uid)
    rows = all_rows(db, "SELECT * FROM care_tasks WHERE family_id=:fid ORDER BY created_at DESC", {"fid": family["id"]})
    return ok([task_view(db, r) for r in rows])


@app.get("/api/care-tasks/{task_id}")
def get_task(task_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Return a single care task from the current family."""
    family = current_family(db, uid)
    row = one(db, "SELECT * FROM care_tasks WHERE id=:id AND family_id=:fid", {"id": task_id, "fid": family["id"]})
    if not row:
        raise BusinessError("照护任务不存在")
    return ok(task_view(db, row))


@app.post("/api/care-tasks")
def create_task(req: TaskRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Create a care task in the current family."""
    family = current_family(db, uid)
    validate_task_request(db, family["id"], req, uid)
    tid, ts = new_id(), now()
    execute(db, """
        INSERT INTO care_tasks(id,family_id,elder_id,title,assignee_user_id,due_at,note,status,created_by_user_id,created_at,updated_at)
        VALUES(:id,:fid,:eid,:title,:assignee,:due,:note,:status,:uid,:created,:updated)
    """, {"id": tid, "fid": family["id"], "eid": req.elderId, "title": req.title.strip(), "assignee": req.assigneeUserId,
          "due": req.dueAt, "note": req.note.strip() if req.note else None, "status": req.status, "uid": uid,
          "created": ts, "updated": ts})
    db.commit()
    return get_task(tid, uid, db)


@app.put("/api/care-tasks/{task_id}")
def update_task(task_id: int, req: TaskRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update a care task in the current family."""
    family = current_family(db, uid)
    if not one(db, "SELECT id FROM care_tasks WHERE id=:id AND family_id=:fid", {"id": task_id, "fid": family["id"]}):
        raise BusinessError("照护任务不存在")
    validate_task_request(db, family["id"], req, uid)
    execute(db, """
        UPDATE care_tasks SET elder_id=:eid,title=:title,assignee_user_id=:assignee,due_at=:due,note=:note,status=:status,updated_at=:updated
        WHERE id=:id
    """, {"eid": req.elderId, "title": req.title.strip(), "assignee": req.assigneeUserId, "due": req.dueAt,
          "note": req.note.strip() if req.note else None, "status": req.status, "updated": now(), "id": task_id})
    db.commit()
    return get_task(task_id, uid, db)


@app.post("/api/care-tasks/{task_id}/complete")
def complete_task(task_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Mark a care task as completed."""
    family = current_family(db, uid)
    if not one(db, "SELECT id FROM care_tasks WHERE id=:id AND family_id=:fid", {"id": task_id, "fid": family["id"]}):
        raise BusinessError("照护任务不存在")
    execute(db, "UPDATE care_tasks SET status='DONE',updated_at=:ts WHERE id=:id", {"ts": now(), "id": task_id})
    db.commit()
    return get_task(task_id, uid, db)


@app.delete("/api/care-tasks/{task_id}")
def delete_task(task_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Delete a care task from the current family."""
    family = current_family(db, uid)
    execute(db, "DELETE FROM care_tasks WHERE id=:id AND family_id=:fid", {"id": task_id, "fid": family["id"]})
    db.commit()
    return ok()


# SOS event APIs
@app.get("/api/sos-events")
def list_sos(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """List SOS events for elders in the current family."""
    family = current_family(db, uid)
    rows = all_rows(db, """
        SELECT s.* FROM sos_events s JOIN elders e ON e.id=s.elder_id
        WHERE e.family_id=:fid ORDER BY s.created_at DESC
    """, {"fid": family["id"]})
    return ok([view_row(r) for r in rows])


@app.post("/api/sos-events/{event_id}/handle")
def handle_sos(event_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Mark an SOS event as being handled by the current user."""
    family = current_family(db, uid)
    event = one(db, """
        SELECT s.* FROM sos_events s JOIN elders e ON e.id=s.elder_id
        WHERE s.id=:id AND e.family_id=:fid
    """, {"id": event_id, "fid": family["id"]})
    if not event:
        raise BusinessError("SOS事件不存在")
    execute(db, "UPDATE sos_events SET status='HANDLING',handled_by_user_id=:uid,handled_at=:ts WHERE id=:id",
            {"uid": uid, "ts": now(), "id": event_id})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM sos_events WHERE id=:id", {"id": event_id})))


@app.post("/api/sos-events/{event_id}/close")
def close_sos(event_id: int, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Close an SOS event and preserve existing handler information."""
    family = current_family(db, uid)
    event = one(db, """
        SELECT s.* FROM sos_events s JOIN elders e ON e.id=s.elder_id
        WHERE s.id=:id AND e.family_id=:fid
    """, {"id": event_id, "fid": family["id"]})
    if not event:
        raise BusinessError("SOS事件不存在")
    execute(db, """
        UPDATE sos_events SET status='CLOSED',handled_by_user_id=COALESCE(handled_by_user_id,:uid),
        handled_at=COALESCE(handled_at,:ts),closed_at=:ts WHERE id=:id
    """, {"uid": uid, "ts": now(), "id": event_id})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM sos_events WHERE id=:id", {"id": event_id})))


# User settings and feedback APIs
class NotificationRequest(BaseModel):
    """Validate the payload for notification operations."""
    sosEnabled: bool
    reminderEnabled: bool
    healthEnabled: bool
    deviceEnabled: bool
    serviceEnabled: bool


@app.get("/api/notification-settings")
def get_notifications(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Return notification preferences, creating defaults when absent."""
    row = one(db, "SELECT * FROM notification_settings WHERE user_id=:uid", {"uid": uid})
    if not row:
        ts = now()
        execute(db, """
            INSERT INTO notification_settings(id,user_id,sos_enabled,reminder_enabled,health_enabled,device_enabled,service_enabled,created_at,updated_at)
            VALUES(:id,:uid,1,1,1,1,0,:created,:updated)
        """, {"id": new_id(), "uid": uid, "created": ts, "updated": ts})
        db.commit()
        row = one(db, "SELECT * FROM notification_settings WHERE user_id=:uid", {"uid": uid})
    return ok(view_row(row))


@app.put("/api/notification-settings")
def update_notifications(req: NotificationRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update notification preferences for the current user."""
    get_notifications(uid, db)
    execute(db, """
        UPDATE notification_settings SET sos_enabled=:sos,reminder_enabled=:reminder,health_enabled=:health,
        device_enabled=:device,service_enabled=:service,updated_at=:ts WHERE user_id=:uid
    """, {"sos": req.sosEnabled, "reminder": req.reminderEnabled, "health": req.healthEnabled,
          "device": req.deviceEnabled, "service": req.serviceEnabled, "ts": now(), "uid": uid})
    db.commit()
    return get_notifications(uid, db)


class PrivacyRequest(BaseModel):
    """Validate the payload for privacy operations."""
    healthVisible: bool
    locationVisible: bool
    deviceVisible: bool


@app.get("/api/privacy-settings")
def get_privacy(uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Return privacy preferences, creating defaults when absent."""
    row = one(db, "SELECT * FROM privacy_settings WHERE user_id=:uid", {"uid": uid})
    if not row:
        ts = now()
        execute(db, """
            INSERT INTO privacy_settings(id,user_id,health_visible,location_visible,device_visible,created_at,updated_at)
            VALUES(:id,:uid,1,0,1,:created,:updated)
        """, {"id": new_id(), "uid": uid, "created": ts, "updated": ts})
        db.commit()
        row = one(db, "SELECT * FROM privacy_settings WHERE user_id=:uid", {"uid": uid})
    return ok(view_row(row))


@app.put("/api/privacy-settings")
def update_privacy(req: PrivacyRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Update privacy preferences for the current user."""
    get_privacy(uid, db)
    execute(db, """
        UPDATE privacy_settings SET health_visible=:health,location_visible=:location,device_visible=:device,updated_at=:ts
        WHERE user_id=:uid
    """, {"health": req.healthVisible, "location": req.locationVisible, "device": req.deviceVisible, "ts": now(), "uid": uid})
    db.commit()
    return get_privacy(uid, db)


class FeedbackRequest(BaseModel):
    """Validate the payload for feedback operations."""
    content: str = Field(min_length=1, max_length=1000)
    contact: Optional[str] = Field(default=None, max_length=100)


@app.post("/api/feedbacks")
def create_feedback(req: FeedbackRequest, uid: int = Depends(current_user_id), db: Session = Depends(get_db)):
    """Store user feedback for later review."""
    fid = new_id()
    execute(db, """
        INSERT INTO feedbacks(id,user_id,content,contact,status,created_at)
        VALUES(:id,:uid,:content,:contact,'NEW',:ts)
    """, {"id": fid, "uid": uid, "content": req.content.strip(), "contact": req.contact.strip() if req.contact else None, "ts": now()})
    db.commit()
    return ok(view_row(one(db, "SELECT * FROM feedbacks WHERE id=:id", {"id": fid})))
