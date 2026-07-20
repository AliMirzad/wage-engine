# ⚙️ Wage Engine (سیستم جامع مدیریت حقوق و دستمزد)

این مخزن شامل کدهای سمت سرور (Backend) سیستم **Wage Engine** است که با استفاده از **Java** و فریم‌ورک **Spring Boot** توسعه داده شده است. این سیستم با معماری ماژولار برای مدیریت پرسنل، قراردادها، پروژه‌ها و محاسبات حقوق با پشتیبانی از سیستم‌های چند-مستاجری (Multi-tenant) طراحی شده است.

---

## 🌟 ویژگی‌های کلیدی (Features)

*   **امنیت و احراز هویت (Security):** پیاده‌سازی کامل Spring Security همراه با توکن‌های JWT، مدیریت Refresh Token و محدودسازی دفعات ورود ناموفق (Login Attempt Limit).
*   **معماری چند-مستاجری (Multi-tenancy):** تفکیک کامل داده‌های مشتریان/سازمان‌های مختلف در سطح دیتابیس (Tenant Context).
*   **ثبت وقایع سیستم (Audit Logging):** ردیابی و ذخیره خودکار تغییرات دیتابیس و اکشن‌های کاربران (AuditorAware).
*   **مدیریت حقوق و دستمزد (Payroll Domain):** ماژول‌های مجزا برای مدیریت کارمندان (Employees)، قراردادها (Contracts) و پروژه‌ها (Projects).
*   **اعتبارسنجی بومی (Custom Validation):** دارای اعتبارسنج‌های اختصاصی از جمله بررسی صحت کد ملی ایران (`@ValidIranianNationalId`).
*   **مدیریت دیتابیس (Migration):** استفاده از Flyway برای کنترل نسخه دیتابیس (شامل `V1__init_security` و `V2__employee_contract_project`).
*   **حذف نرم (Soft Delete):** پیاده‌سازی مکانیزم Soft Deletable برای جلوگیری از حذف فیزیکی اطلاعات مهم.

---

## 🛠 تکنولوژی‌های استفاده شده (Tech Stack)

*   **زبان برنامه‌نویسی:** Java
*   **فریم‌ورک اصلی:** Spring Boot
*   **مدیریت وابستگی‌ها:** Maven
*   **امنیت:** Spring Security + JWT Token
*   **مدیریت دیتابیس و مایگریشن:** Flyway
*   **پایگاه داده:** PostgreSQL / MySQL (از طریق تنظیمات `application.yml`)
*   **چندزبانه بودن (I18N):** پشتیبانی از پیام‌های فارسی (`messages_fa.properties`)

---

## 🏗 معماری و ساختار پروژه (Project Structure)

ساختار پکیج‌های این پروژه (در مسیر `ir.manaz`) به شکل زیر لایه‌بندی شده است:

```text
src/main/java/ir/manaz/
 ├── audit/           # سیستم ثبت وقایع و لاگ‌گیری (AuditLog, AuditEvent)
 ├── common/          # کلاس‌های پایه، Responseها و اعتبارسنجی‌ها (NationalIdValidator)
 ├── config/          # تنظیمات اصلی اسپرینگ (Security, Flyway, AppProperties)
 ├── exception/       # مدیریت متمرکز خطاها (GlobalExceptionHandler)
 ├── payroll/         # منطق اصلی کسب‌وکار (Employee, Contract, Project)
 ├── security/        # سیستم امنیتی، JWT، مدیریت نقش‌ها، توکن‌ها و UserDetails
 └── tenant/          # پیاده‌سازی Multi-tenancy و Tenant Context

```

---

## 🚀 راهنمای نصب و اجرا (Getting Started)

### ۱. تنظیمات دیتابیس

ابتدا یک دیتابیس خالی ایجاد کنید. اطلاعات اتصال به دیتابیس را در فایل `src/main/resources/application.yml` تنظیم کنید.

### ۲. بیلد و اجرای مایگریشن‌ها

با اجرای پروژه، اسکریپت‌های Flyway (موجود در پوشه `db/migration`) به صورت خودکار اجرا شده و جداول مربوط به امنیت و حقوق و دستمزد را می‌سازند.

```bash
mvn clean install

```

### ۳. اجرای سرویس

```bash
mvn spring-boot:run

```

> **نکته:** سوپر ادمین پیش‌فرض هنگام بالا آمدن برنامه از طریق کلاس `SuperAdminSeeder` در دیتابیس ایجاد می‌شود.

---

## 🔌 مستندات رابط‌های برنامه‌نویسی (API Endpoints)

در این بخش مسیرهای اصلی API که برای اتصال کلاینت (فرانت‌اند) به سرور آماده شده‌اند، لیست شده است.
*(نکته: برای دسترسی به اکثر این مسیرها، ارسال `Bearer Token` در هدر `Authorization` الزامی است.)*

### 🔐 ماژول احراز هویت (Auth API - `AuthController`)

* `POST /api/auth/login` : ورود به سیستم و دریافت JWT و Refresh Token
* `POST /api/auth/register` : ثبت‌نام کاربر جدید
* `POST /api/auth/refresh-token` : دریافت توکن جدید با استفاده از Refresh Token
* `POST /api/auth/logout` : خروج از سیستم و ابطال توکن‌ها
* `POST /api/auth/forgot-password` : درخواست فراموشی رمز عبور (ارسال لینک/کد)
* `POST /api/auth/reset-password` : تنظیم رمز عبور جدید
* `GET  /api/auth/user-info` : دریافت اطلاعات کاربر لاگین شده فعلی

### 👥 ماژول کارمندان (Employee API)

* `POST /api/employees` : ثبت کارمند جدید (همراه با اعتبارسنجی کد ملی)
* `GET  /api/employees` : دریافت لیست کارمندان (پشتیبانی از Pagination و فیلتر)
* `GET  /api/employees/{id}` : دریافت اطلاعات دقیق یک کارمند
* `PUT  /api/employees/{id}` : ویرایش اطلاعات کارمند
* `DELETE /api/employees/{id}` : حذف کارمند (به صورت Soft Delete)

### 📄 ماژول قراردادها (Contract API)

* `POST /api/contracts` : ثبت قرارداد جدید برای کارمند
* `GET  /api/contracts` : لیست قراردادهای سازمان
* `GET  /api/contracts/employee/{employeeId}` : دریافت لیست قراردادهای یک کارمند خاص
* `PUT  /api/contracts/{id}` : به‌روزرسانی وضعیت/مفاد قرارداد

### 🏢 ماژول پروژه‌ها (Project API)

* `POST /api/projects` : تعریف پروژه جدید
* `GET  /api/projects` : دریافت لیست پروژه‌ها
* `PUT  /api/projects/{id}` : ویرایش جزئیات پروژه
* `DELETE /api/projects/{id}` : بایگانی/حذف پروژه
