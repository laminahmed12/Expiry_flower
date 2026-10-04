"""
تطبيق الخادم البرمجي (Backend) - نظام إدارة ومتابعة تواريخ الصلاحية
Python FastAPI + SQLAlchemy + SQLite
يوفر واجهة برمجية متكاملة لـ:
1. قاعدة بيانات المواد (أغذية، استهلاكية، أدوية)
2. نظام التنبيه الصوتي (Audio & Text-to-Speech)
3. النسخ الاحتياطي والتصدير والاستيراد (Backup & Restore)
4. نظام التراخيص وفترة التجربة (10 أيام تجريبية + رموز تفعيل)
5. لوحة التحكم الإدارية السرية (Adreemk) برمز الحماية (116936)
"""

import os
import uuid
import datetime
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Header, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy import create_engine, Column, Integer, String, Boolean, DateTime
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

DATABASE_URL = "sqlite:///./expiry_guard.db"

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

SECRET_ADMIN_PASSCODE = "116936"
TRIAL_PERIOD_DAYS = 10

# ----------------- Database Models -----------------
class StoredItemDB(Base):
    __tablename__ = "stored_items"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True, nullable=False) # اسم المادة
    category = Column(String, nullable=False) # FOOD, CONSUMABLES, MEDICINE
    production_date = Column(String, nullable=False) # تاريخ الإنتاج YYYY-MM-DD
    expiry_date = Column(String, nullable=False) # تاريخ الانتهاء YYYY-MM-DD
    quantity = Column(Integer, default=1) # الكمية
    storage_location = Column(String, default="") # مكان التخزين
    image_uri = Column(String, nullable=True) # مسار صورة المنتج
    barcode = Column(String, nullable=True, index=True) # رمز الباركود
    notes = Column(String, default="") # ملاحظات إضافية
    is_sensitive = Column(Boolean, default=False) # مادة حساسة (مثل الأدوية)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

class ActivationCodeDB(Base):
    __tablename__ = "activation_codes"

    code = Column(String, primary_key=True, index=True)
    duration_months = Column(Integer, nullable=False) # 6, 12, or -1 (دائم)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    is_revoked = Column(Boolean, default=False)
    is_activated = Column(Boolean, default=False)
    activated_at = Column(DateTime, nullable=True)
    note = Column(String, default="")

class AppSettingsDB(Base):
    __tablename__ = "app_settings"

    key = Column(String, primary_key=True)
    value = Column(String, nullable=False)

Base.metadata.create_all(bind=engine)

# ----------------- Pydantic Schemas -----------------
class StoredItemCreate(BaseModel):
    name: str
    category: str # FOOD, CONSUMABLES, MEDICINE
    production_date: str
    expiry_date: str
    quantity: int = 1
    storage_location: str = ""
    image_uri: Optional[str] = None
    barcode: Optional[str] = None
    notes: str = ""
    is_sensitive: bool = False

class StoredItemOut(StoredItemCreate):
    id: int
    days_remaining: int
    status: str # EXPIRED, EXPIRING_SOON, SAFE

    class Config:
        orm_mode = True

class ActivationCodeCreate(BaseModel):
    duration_months: int # 6, 12, -1
    note: str = ""

class LicenseStatusOut(BaseModel):
    is_trial_active: bool
    trial_days_remaining: int
    is_licensed: bool
    license_type: str
    is_access_allowed: bool
    active_code: Optional[str] = None

# ----------------- Helper Functions -----------------
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def get_setting(db: Session, key: str, default: str = "") -> str:
    setting = db.query(AppSettingsDB).filter(AppSettingsDB.key == key).first()
    return setting.value if setting else default

def set_setting(db: Session, key: str, value: str):
    setting = db.query(AppSettingsDB).filter(AppSettingsDB.key == key).first()
    if setting:
        setting.value = value
    else:
        setting = AppSettingsDB(key=key, value=value)
        db.add(setting)
    db.commit()

def calculate_days_remaining(expiry_date_str: str) -> int:
    try:
        exp_date = datetime.datetime.strptime(expiry_date_str, "%Y-%m-%d").date()
        today = datetime.date.today()
        return (exp_date - today).days
    except Exception:
        return 999

# ----------------- FastAPI App -----------------
app = FastAPI(
    title="منظومة متابعة تواريخ الصلاحية (ExpiryGuard API)",
    description="خلفية برمجية متكاملة لإدارة ومتابعة تواريخ الصلاحية ونظام التراخيص والتنبيه الصوتي",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------------- Item Endpoints -----------------
@app.get("/api/items")
def get_all_items(category: Optional[str] = None, db: Session = Depends(get_db)):
    query = db.query(StoredItemDB)
    if category:
        query = query.filter(StoredItemDB.category == category)
    items = query.all()

    alert_threshold = int(get_setting(db, "alert_threshold_days", "30"))

    result = []
    for item in items:
        days = calculate_days_remaining(item.expiry_date)
        status = "EXPIRED" if days < 0 else "EXPIRING_SOON" if days <= alert_threshold else "SAFE"
        result.append({
            "id": item.id,
            "name": item.name,
            "category": item.category,
            "production_date": item.production_date,
            "expiry_date": item.expiry_date,
            "quantity": item.quantity,
            "storage_location": item.storage_location,
            "image_uri": item.image_uri,
            "barcode": item.barcode,
            "notes": item.notes,
            "is_sensitive": item.is_sensitive,
            "days_remaining": days,
            "status": status
        })
    return result

@app.post("/api/items")
def create_item(payload: StoredItemCreate, db: Session = Depends(get_db)):
    item = StoredItemDB(
        name=payload.name,
        category=payload.category,
        production_date=payload.production_date,
        expiry_date=payload.expiry_date,
        quantity=payload.quantity,
        storage_location=payload.storage_location,
        image_uri=payload.image_uri,
        barcode=payload.barcode,
        notes=payload.notes,
        is_sensitive=payload.is_sensitive or (payload.category == "MEDICINE")
    )
    db.add(item)
    db.commit()
    db.refresh(item)
    return {"message": "تم إضافة المادة بنجاح", "id": item.id}

@app.delete("/api/items/{item_id}")
def delete_item(item_id: int, db: Session = Depends(get_db)):
    item = db.query(StoredItemDB).filter(StoredItemDB.id == item_id).first()
    if not item:
        raise HTTPException(status_code=404, detail="المادة غير موجودة")
    db.delete(item)
    db.commit()
    return {"message": "تم حذف المادة بنجاح"}

# ----------------- Audio & Text-to-Speech Alert -----------------
@app.get("/api/alerts/check")
def check_expiry_alerts(db: Session = Depends(get_db)):
    items = db.query(StoredItemDB).all()
    alert_threshold = int(get_setting(db, "alert_threshold_days", "30"))

    expired_list = []
    soon_list = []
    sensitive_medicines = []

    for item in items:
        days = calculate_days_remaining(item.expiry_date)
        if days < 0:
            expired_list.append({"name": item.name, "category": item.category, "days": abs(days)})
        elif days <= alert_threshold:
            soon_list.append({"name": item.name, "category": item.category, "days": days})
            if item.category == "MEDICINE" or item.is_sensitive:
                sensitive_medicines.append(item.name)

    # توليد النص الصوتي المنطوق
    tts_text = "تقرير تنبيهات الصلاحية: "
    if expired_list:
        tts_text += f"يوجد {len(expired_list)} مواد منتهية الصلاحية يجب إتلافها فوراً. "
    if soon_list:
        tts_text += f"وهناك {len(soon_list)} مواد أوشكت على الانتهاء. "
    if sensitive_medicines:
        tts_text += f"تحذير هام: تم رصد أدوية حساسة تتطلب فحصاً فورياً: {', '.join(sensitive_medicines)}."

    return {
        "expired_count": len(expired_list),
        "soon_count": len(soon_list),
        "sensitive_medicines": sensitive_medicines,
        "tts_alert_text": tts_text,
        "play_alarm_tone": len(sensitive_medicines) > 0
    }

# ----------------- Backup & Restore (JSON) -----------------
@app.get("/api/backup/export")
def export_backup(db: Session = Depends(get_db)):
    items = db.query(StoredItemDB).all()
    codes = db.query(ActivationCodeDB).all()

    items_data = [
        {
            "id": i.id, "name": i.name, "category": i.category,
            "production_date": i.production_date, "expiry_date": i.expiry_date,
            "quantity": i.quantity, "storage_location": i.storage_location,
            "image_uri": i.image_uri, "barcode": i.barcode, "notes": i.notes,
            "is_sensitive": i.is_sensitive
        } for i in items
    ]

    codes_data = [
        {
            "code": c.code, "duration_months": c.duration_months,
            "is_revoked": c.is_revoked, "is_activated": c.is_activated, "note": c.note
        } for c in codes
    ]

    return {
        "app": "ExpiryGuard",
        "export_date": datetime.datetime.utcnow().isoformat(),
        "items": items_data,
        "codes": codes_data
    }

@app.post("/api/backup/import")
def import_backup(data: dict, db: Session = Depends(get_db)):
    if "items" not in data:
        raise HTTPException(status_code=400, detail="صيغة النسخة الاحتياطية غير صالحة")

    restored_count = 0
    for item_data in data["items"]:
        item = StoredItemDB(
            name=item_data["name"],
            category=item_data["category"],
            production_date=item_data["production_date"],
            expiry_date=item_data["expiry_date"],
            quantity=item_data.get("quantity", 1),
            storage_location=item_data.get("storage_location", ""),
            image_uri=item_data.get("image_uri"),
            barcode=item_data.get("barcode"),
            notes=item_data.get("notes", ""),
            is_sensitive=item_data.get("is_sensitive", False)
        )
        db.add(item)
        restored_count += 1
    db.commit()

    return {"message": f"تم استعادة {restored_count} مادة بنجاح!"}

# ----------------- Licensing & 10-Day Trial -----------------
@app.get("/api/license/status", response_model=LicenseStatusOut)
def get_license_status(db: Session = Depends(get_db)):
    trial_start_str = get_setting(db, "trial_start_timestamp")
    now = datetime.datetime.utcnow()

    if not trial_start_str:
        set_setting(db, "trial_start_timestamp", now.isoformat())
        trial_days_left = TRIAL_PERIOD_DAYS
    else:
        trial_start = datetime.datetime.fromisoformat(trial_start_str)
        elapsed = (now - trial_start).days
        trial_days_left = max(0, TRIAL_PERIOD_DAYS - elapsed)

    is_trial_active = trial_days_left > 0
    active_code = get_setting(db, "active_license_code")
    is_licensed = False
    license_type = f"فترة تجريبية ({trial_days_left} أيام متبقية)"

    if active_code:
        code_obj = db.query(ActivationCodeDB).filter(ActivationCodeDB.code == active_code).first()
        if code_obj and not code_obj.is_revoked:
            is_licensed = True
            license_type = "ترخيص دائم" if code_obj.duration_months == -1 else f"ترخيص {code_obj.duration_months} أشهر"

    return LicenseStatusOut(
        is_trial_active=is_trial_active,
        trial_days_remaining=trial_days_left,
        is_licensed=is_licensed,
        license_type=license_type,
        is_access_allowed=is_licensed or is_trial_active,
        active_code=active_code
    )

@app.post("/api/license/activate")
def activate_license(code: str, db: Session = Depends(get_db)):
    code_obj = db.query(ActivationCodeDB).filter(ActivationCodeDB.code == code.strip().upper()).first()
    if not code_obj:
        raise HTTPException(status_code=400, detail="رمز التفعيل غير موجود أو غير صالح")
    if code_obj.is_revoked:
        raise HTTPException(status_code=400, detail="تم إلغاء أو إيقاف هذا الرمز من قبل الإدارة")

    code_obj.is_activated = True
    code_obj.activated_at = datetime.datetime.utcnow()
    set_setting(db, "active_license_code", code_obj.code)
    db.commit()
    return {"message": "تم تفعيل الترخيص بنجاح!"}

# ----------------- Adreemk Hidden Admin Panel (Passcode 116936) -----------------
def verify_admin_passcode(x_adreemk_pin: str = Header(None)):
    if x_adreemk_pin != SECRET_ADMIN_PASSCODE:
        raise HTTPException(status_code=403, detail="رمز المرور الإداري غير صحيح")
    return True

@app.post("/api/admin/adreemk/generate-code", dependencies=[Depends(verify_admin_passcode)])
def generate_code(payload: ActivationCodeCreate, db: Session = Depends(get_db)):
    prefix = {6: "ADR-6M-", 12: "ADR-1Y-", -1: "ADR-PERM-"}.get(payload.duration_months, "ADR-CODE-")
    code_str = f"{prefix}{uuid.uuid4().hex[:8].upper()}"

    new_code = ActivationCodeDB(
        code=code_str,
        duration_months=payload.duration_months,
        note=payload.note
    )
    db.add(new_code)
    db.commit()
    return {"code": code_str, "duration_months": payload.duration_months, "note": payload.note}

@app.get("/api/admin/adreemk/codes", dependencies=[Depends(verify_admin_passcode)])
def list_all_codes(db: Session = Depends(get_db)):
    return db.query(ActivationCodeDB).order_by(ActivationCodeDB.created_at.desc()).all()

@app.post("/api/admin/adreemk/toggle-revoke", dependencies=[Depends(verify_admin_passcode)])
def toggle_revoke_code(code: str, revoke: bool, db: Session = Depends(get_db)):
    code_obj = db.query(ActivationCodeDB).filter(ActivationCodeDB.code == code).first()
    if not code_obj:
        raise HTTPException(status_code=404, detail="الرمز غير موجود")
    code_obj.is_revoked = revoke
    db.commit()
    return {"message": "تم إيقاف تفعيل الرمز بنجاح" if revoke else "تم إعادة تنشيط الرمز"}

@app.post("/api/admin/adreemk/reset-trial", dependencies=[Depends(verify_admin_passcode)])
def reset_trial(db: Session = Depends(get_db)):
    set_setting(db, "trial_start_timestamp", datetime.datetime.utcnow().isoformat())
    return {"message": "تمت إعادة تعيين فترة الـ 10 أيام التجريبية"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
