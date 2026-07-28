# راهنمای پیاده‌سازی فرانت‌اند — APIهای کاربر و احراز هویت

این سند برای هوش مصنوعی/توسعه‌دهنده‌ای است که فرانت پنل مدیریت را می‌سازد.
همه‌ی جزئیات لازم برای اتصال به بک‌اند در همین فایل هست؛ به کد بک‌اند نگاه نکن.

- Base URL دِو: `http://localhost:8080`
- محیط: تمام درخواست‌ها `Content-Type: application/json` و پاسخ‌ها JSON UTF-8.
- پیام خطا برای کاربر: از فیلد `message` استفاده کن (فارسی است).
- شاخه‌بندی منطق: از فیلد `code` استفاده کن (ماشین‌خوان، مثلاً `auth.invalid_credentials`).

---

## 1) پوسته‌ی پاسخ خطا (Error Envelope)

هر پاسخ ناموفق (4xx/5xx) این شکل را دارد:

```json
{
  "timestamp": "2026-07-28T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "auth.invalid_credentials",
  "message": "نام کاربری یا رمز عبور اشتباه است",
  "path": "/api/v1/auth/login"
}
```

منطق فرانت را روی `code` و `status` بساز، `message` را برای toast/alert نشان بده.
یک هلپر `parseError(res)` بنویس که این ساختار را برگرداند.

---

## 2) توکن‌ها و ذخیره‌سازی

- `accessToken`: JWT کوتاه‌عمر (پیش‌فرض **۱۵ دقیقه**). در هر درخواست حفاظت‌شده در هدر:
  `Authorization: Bearer <accessToken>`
- `refreshToken`: JWT بلندعمر (**۷ روز**). فقط برای `/api/v1/auth/refresh`.
- ذخیره‌سازی پیشنهادی: هر دو در `localStorage` (کوکی نیست چون بک‌اند CSRF ندارد و اصلاً از کوکی استفاده نمی‌کند). اگر می‌خواهی XSS-hardening بکنی، `sessionStorage` + auto-logout در تب بستن.
- claimهای مفید داخل access token: `sub` (username)، `uid` (userId)، `tid` (tenantId، برای SUPER_ADMIN وجود ندارد)، `auth` (لیست ROLE_* و permissionها).
- **قانون طلایی**: منبع واقعی پروفایل و دسترسی‌ها **`GET /api/v1/auth/me`** است، نه توکن. بعد از login و بعد از هر refresh، `me` را صدا بزن و منو/دسترسی‌ها را از آن رِندر کن.

### Interceptor پیشنهادی (axios/fetch wrapper)

- روی هر response با `401` و `code === "auth.token.expired"`: یک‌بار `/auth/refresh` بزن، توکن نو را جایگزین کن، درخواست اصلی را دوباره بفرست.
- روی `401` با هر code دیگر یا شکست refresh: `logout()` محلی + هدایت به صفحه ورود.
- روی `403` (`auth.forbidden`): toast «دسترسی ندارید» + مسیر خنثی؛ لاگ‌اوت نکن.
- روی `429` (`error.rate_limited`): toast «تعداد درخواست‌ها زیاد است، کمی صبر کنید» و دکمه‌ی مربوطه را تا `Retry-After` (ثانیه) غیرفعال کن.

---

## 3) نقش‌ها و دسترسی‌ها (Permissions)

نقش‌های سیستمی:

| نقش             | توضیح                                                       |
|-----------------|--------------------------------------------------------------|
| `SUPER_ADMIN`   | پلتفرم — دسترسی به هر tenant. `tenantId=null` دارد.           |
| `COMPANY_ADMIN` | مدیر شرکت — کامل روی داده‌های شرکت خودش.                       |
| `ACCOUNTANT`    | حسابدار عملیاتی — روزمره‌ی حقوق و دستمزد.                       |
| `MANAGER`       | مدیریت/گزارش‌گیری فقط خواندنی و تأیید حقوق.                    |
| `EMPLOYEE`      | کارمند — فقط فیش خودش را می‌بیند.                                |
| `AUDITOR`       | فقط خواندنی برای ممیزی + دسترسی audit log.                   |

دسترسی‌ها که در فرانت مهم‌اند (برای نمایش/پنهان‌کردن منو):

```
USER_READ, USER_WRITE
ROLE_READ, ROLE_WRITE
TENANT_READ, TENANT_WRITE
PROJECT_READ, PROJECT_WRITE, PROJECT_FINANCIAL_READ
EMPLOYEE_READ, EMPLOYEE_WRITE, EMPLOYEE_DELETE
CONTRACT_READ, CONTRACT_WRITE
PERFORMANCE_READ, PERFORMANCE_WRITE
PAYROLL_CALCULATE, PAYROLL_READ, PAYROLL_APPROVE
PAYSLIP_READ_OWN, PAYSLIP_READ_ALL, PAYSLIP_PRINT
BANK_FILE_EXPORT
SETTINGS_READ, SETTINGS_WRITE
REPORT_READ, REPORT_EXPORT
AUDIT_LOG_READ
```

قانون رِندر منو: `me.permissions.includes("USER_WRITE")` → نمایش دکمه «افزودن کاربر».

---

## 4) Endpointهای احراز هویت — `/api/v1/auth/*`

### 4.1 `POST /login` — عمومی

```json
// Request
{ "usernameOrEmail": "acme.admin", "password": "ChangeMe@123" }
```

```json
// 200 OK
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 42, "tenantId": 7, "username": "acme.admin", "email": "admin@acme.com",
    "emailVerified": false,
    "firstName": "علی", "lastName": "احمدی",
    "roles": ["COMPANY_ADMIN"],
    "permissions": ["USER_READ","USER_WRITE", ...]
  }
}
```

**فیلد `emailVerified`**: اگر `false` بود، فرانت باید بنر «ایمیلتان را تأیید کنید» + دکمه‌ی «ارسال مجدد کد» + فرم وارد کردن کد نشان دهد. تا قبل از تأیید، endpoint `/forgot-password` برای این کاربر بی‌اثر است (سکوت).

خطاهای مهم:

| status | code                        | معنی برای فرانت                                            |
|--------|-----------------------------|-------------------------------------------------------------|
| 401    | `auth.invalid_credentials`  | نام کاربری یا رمز غلط.                                      |
| 401    | `auth.account_locked`       | حساب بعد از ۵ شکست قفل شد؛ ۱۵ دقیقه صبر یا reset از ادمین.|
| 401    | `auth.account_disabled`     | حساب توسط ادمین غیرفعال شده. با پشتیبانی/ادمین شرکت تماس.  |
| 401    | `auth.tenant_inactive`      | شرکت غیرفعال است؛ پیام «با پشتیبانی تماس بگیرید».         |
| 429    | `error.rate_limited`        | زیادی تلاش کردی از این IP؛ ۶۰ ثانیه صبر.                    |

**Rate limit**: ۲۰ درخواست/دقیقه از هر IP روی login/refresh/reset-password، ۵/دقیقه روی forgot-password.

### 4.2 `POST /refresh` — عمومی

```json
{ "refreshToken": "..." }
```

پاسخ عیناً مثل login (توکن‌های جدید + user). **refresh token هر بار عوض می‌شود** (rotation). اگر refresh با توکن قدیمی زده شود، تمام sessionهای کاربر باطل می‌شوند (defense برای replay). یعنی: هرگز refreshToken قدیمی را نگه ندار — بلافاصله جایگزین کن.

### 4.3 `POST /logout` — نیازمند احراز

```json
{ "refreshToken": "..." }
```

پاسخ `204 No Content`. فقط refresh token همین session باطل می‌شود. access token فعلی تا انقضا (۱۵ دقیقه) هنوز کار می‌کند ولی قابل تمدید نیست. فرانت باید بعدش تمام state احراز را پاک کند.

### 4.4 `GET /me` — نیازمند احراز

پاسخ همان بلاک `user` بالا. **در هر بار بارگذاری اپ و بعد از هر refresh این را صدا بزن** تا اگر ادمین دسترسی‌ها را عوض کرده، بلافاصله بدون انتظار برای انقضای access token در UI اعمال شود.

خطاها: `401 auth.account_disabled` اگر بین توکن گرفتن و صدا زدن، ادمین حساب را غیرفعال کرده.

### 4.5 `POST /change-password` — نیازمند احراز

```json
{ "currentPassword": "OldPass1", "newPassword": "NewPass2" }
```

پاسخ `204`. پس از موفقیت، **همه‌ی refresh tokenهای دیگر revoke می‌شوند** (این session را بک‌اند نگه می‌دارد؟ نه — همه revoke می‌شوند، پس فرانت باید کاربر را logout و به login بفرستد).

خطاها:

| status | code                          | معنی                                            |
|--------|-------------------------------|--------------------------------------------------|
| 401    | `auth.password.current.wrong` | رمز فعلی غلط.                                     |
| 400    | `error.validation`            | رمز جدید policy را رعایت نکرده (پیام دقیق در `message`). |

Policy رمز: حداقل ۸ کاراکتر، شامل حداقل یک حرف و یک رقم.

### 4.6 `POST /forgot-password` — عمومی

```json
{ "email": "user@acme.com" }
```

**همیشه `204` برمی‌گرداند**، حتی اگر ایمیل وجود نداشته، کاربر غیرفعال، یا ایمیل تأیید نشده باشد — عمداً به‌قصد جلوگیری از enumeration. کد ۶ رقمی OTP به ایمیل ارسال می‌شود و **۱۰ دقیقه** معتبر است. حداکثر ۵ تلاش غلط قبل از invalidate کد.

UI: «اگر این ایمیل ثبت و تأیید شده باشد، کد بازیابی رمز به آن ارسال می‌شود.»

**نکته مهم**: اگر کاربر ایمیلش را هنوز تأیید نکرده باشد (`emailVerified=false`)، این endpoint سکوت می‌کند. UI باید کاربر را راهنمایی کند اول ایمیل را از داخل پنل تأیید کند.

### 4.7 `POST /reset-password` — عمومی

```json
{
  "email": "user@acme.com",
  "code": "123456",
  "newPassword": "NewPass1"
}
```

- `email`: همان ایمیلی که کد به آن ارسال شده.
- `code`: کد ۶ رقمی از ایمیل (فرمت اجباری `^\d{6}$`).
- `newPassword`: باید policy را رعایت کند (حداقل ۸ کاراکتر، حرف و عدد).

پاسخ `204`. بعد از موفقیت، **همه‌ی sessionها باطل می‌شوند** — فرانت باید کاربر را به صفحه login هدایت کند.

خطاها:

| status | code                | معنی                                                            |
|--------|---------------------|------------------------------------------------------------------|
| 401    | `otp.invalid`       | کد غلط / منقضی / مصرف‌شده / تلاش‌های مجاز تمام شده.               |
| 400    | `error.validation`  | فرمت کد یا رمز policy را رعایت نمی‌کند.                          |

### 4.8 `POST /verify-email` — نیازمند احراز

تأیید ایمیل کاربر جاری با کد ۶ رقمی که موقع onboarding به ایمیلش ارسال شده.

```json
{ "code": "123456" }
```

پاسخ `204`. اگر ایمیل قبلاً تأیید شده باشد، **idempotent** — همان `204` برمی‌گردد.

خطاها:

| status | code                | معنی                                                       |
|--------|---------------------|-------------------------------------------------------------|
| 401    | `otp.invalid`       | کد غلط / منقضی / تلاش‌های مجاز تمام.                        |
| 400    | `error.validation`  | فرمت کد نامعتبر.                                            |

### 4.9 `POST /resend-verification` — نیازمند احراز

ارسال مجدد کد تأیید ایمیل به کاربر جاری. body ندارد.

پاسخ همیشه `204` — حتی اگر ایمیل قبلاً تأیید شده (idempotent، ولی هیچ ایمیل تازه‌ای فرستاده نمی‌شود). rate limit مثل forgot-password (۵/دقیقه/IP).

---

## 5) Endpointهای مدیریت کاربران شرکت — `/api/v1/users/*`

مسیر مخصوص **مدیر شرکت** (COMPANY_ADMIN با `USER_READ`/`USER_WRITE`). شناسه‌ی شرکت از توکن خوانده می‌شود؛ در URL نیست.

### 5.1 `GET /api/v1/users` — لیست

پارامترها: `?page=0&size=20&sort=createdAt,desc`.

پاسخ `PageResponse<UserResponse>`:

```json
{
  "content": [
    {
      "id": 42, "username": "acme.accountant", "email": "hesabdar@acme.com",
      "firstName": "زهرا", "lastName": "کریمی",
      "enabled": true, "locked": false, "lastLoginAt": "2026-07-27T09:12:00Z",
      "roles": ["ACCOUNTANT"]
    }
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
}
```

خطای مهم: `400 user.tenant_required` اگر کاربر جاری به هیچ شرکتی تعلق ندارد (مثلاً SUPER_ADMIN تصادفاً این را زده). در UI SUPER_ADMIN این منو را نشان نده.

### 5.2 `GET /api/v1/users/{id}` — یک کاربر

پاسخ `UserResponse` (همان ساختار بالا). خطا `404 user.not_found`.

### 5.3 `POST /api/v1/users` — ساخت

```json
{
  "username": "acme.new",
  "email": "new@acme.com",
  "firstName": "علی",
  "lastName": "رضایی",
  "roleNames": ["ACCOUNTANT"]
}
```

پاسخ `201 Created`:

```json
{
  "user": { /* UserResponse */ },
  "initialPassword": "k7Rm2Xq9"
}
```

**`initialPassword` فقط همین یک بار برمی‌گردد** — در هیچ جای دیگری قابل بازیابی نیست. UI باید آن را در یک مودال «کپی کن»/«دانلود»/«پرینت» به مدیر بدهد و توضیح دهد که به کاربر تحویل شود. کاربر هم پس از اولین ورود از `/auth/change-password` عوض می‌کند.

نکات:
- username و email در کل سامانه یکتا هستند (نه فقط داخل شرکت).
- نقش‌های سیستمی مجاز از این مسیر: `ACCOUNTANT`, `MANAGER`, `EMPLOYEE`, `AUDITOR`. اگر `COMPANY_ADMIN` یا `SUPER_ADMIN` بفرستی → `400 user.role.not_assignable`.
- نقش‌های سفارشی همان شرکت هم مجازند مگر دسترسی خطرناک (`TENANT_*`, `ROLE_WRITE`) داشته باشند.

خطاها:

| status | code                          | معنی                                           |
|--------|-------------------------------|-------------------------------------------------|
| 409    | `user.username.duplicate`     | نام کاربری تکراری.                              |
| 409    | `user.email.duplicate`        | ایمیل تکراری.                                    |
| 400    | `user.role.not_assignable`    | نقش انتخاب‌شده از این مسیر قابل تخصیص نیست.     |
| 404    | `role.not_found`              | نقش با این نام وجود ندارد.                       |
| 400    | `error.validation`            | داده‌ی نامعتبر (طول/فرمت/خالی).                  |

### 5.4 `PUT /api/v1/users/{id}` — ویرایش

```json
{
  "email": "new@acme.com",       // optional
  "firstName": "علی",             // optional
  "lastName": "رضایی",            // optional
  "roleNames": ["ACCOUNTANT"]    // optional — اگر بفرستی، جایگزین کامل می‌شود
}
```

- `username` **غیرقابل تغییر** است.
- اگر `roleNames` را نفرستی، نقش‌ها دست‌نخورده می‌مانند.
- اگر `roleNames: []` بفرستی → `400 user.roles.required`.
- کاربر نمی‌تواند حساب خودش را از این مسیر ویرایش کند → `400 user.cannot_modify_self`. برای تغییرات پروفایل خودش باید endpoint جداگانه‌ای اضافه شود (فعلاً نیست — از /change-password فقط رمز).

پاسخ `200` با `UserResponse` جدید.

### 5.5 `POST /api/v1/users/{id}/deactivate` — غیرفعال‌سازی

پاسخ `204`.
- کاربر دیگر نمی‌تواند وارد شود.
- همه‌ی refresh tokenها و password reset tokensش باطل می‌شوند.
- access token فعلی تا ۱۵ دقیقه معتبر است ولی قابل تمدید نیست.
- حذف واقعی وجود ندارد — تاریخچه‌ی حسابداری باید حفظ شود.

خطاها: `400 user.cannot_deactivate_self`, `409 user.already_disabled`.

### 5.6 `POST /api/v1/users/{id}/activate` — فعال‌سازی مجدد

پاسخ `204`. قفل ناشی از تلاش ناموفق هم پاک می‌شود. خطا: `409 user.already_enabled`.

### 5.7 `POST /api/v1/users/{id}/reset-password` — بازنشانی توسط مدیر

پاسخ `200`:

```json
{ "initialPassword": "n3Ka7fQr" }
```

مثل ساخت کاربر: **فقط یک بار برمی‌گردد**. همه‌ی sessionهای کاربر باطل و قفل حساب پاک می‌شود.
- مدیر نمی‌تواند رمز خودش را از این مسیر عوض کند → `400 user.cannot_reset_own_password`. باید از `/auth/change-password` استفاده کند.

### مسیر خود کاربر (self-service)
هنوز endpoint «ویرایش پروفایل خود» وجود ندارد. کاربر معمولی فقط می‌تواند رمز خودش را با `/auth/change-password` عوض کند.

---

## 6) Endpointهای SUPER_ADMIN — `/api/v1/admin/tenants/*`

فقط دسترسی‌های `TENANT_READ`/`TENANT_WRITE`. مسیر onboarding مشتری جدید و مدیریت شرکت‌ها.

### 6.0 `POST /api/v1/admin/tenants` — **ساخت شرکت + مدیر اولش (onboarding)**

این مهم‌ترین endpoint SUPER_ADMIN است. شرکت و اولین `COMPANY_ADMIN` را در یک تراکنش می‌سازد. اگر یکی fail شود، هیچ چیز ثبت نمی‌شود — هرگز شرکت بی‌مدیر نداریم.

```json
// Request
{
  "name": "شرکت آکمه",
  "code": "acme",
  "nationalId": "10101234567",
  "insuranceWorkshopCode": "1234567890",
  "economicCode": "411111111111",
  "iban": "IR820540102680020817909002",
  "address": "تهران، خیابان ...",
  "phone": "02112345678",
  "adminUsername": "acme.admin",
  "adminEmail": "admin@acme.com",
  "adminPassword": "InitPass1",
  "adminFirstName": "علی",
  "adminLastName": "رضایی"
}
```

**اجباری**: `name`, `code`, `adminUsername`, `adminEmail`, `adminPassword`.
**اختیاری**: بقیه — مدیر شرکت خودش بعداً با `PUT /api/v1/my-company` تکمیل می‌کند.

**قوانین حیاتی**:
- `code` شرکت پس از ثبت **قابل تغییر نیست**.
- `adminUsername` و `adminEmail` **سراسری یکتا** (نه فقط داخل شرکت).
- `adminPassword` باید policy را رعایت کند (حداقل ۸ کاراکتر، حرف و عدد). این رمز را SUPER_ADMIN تعیین می‌کند (auto-generate نیست) چون باید امن به مشتری تحویل شود؛ مدیر اولین ورود با `/auth/change-password` عوض می‌کند.
- `iban` اگر ارسال شود باید `^IR\d{24}$` باشد.

```json
// 201 Created
{
  "id": 7,
  "name": "شرکت آکمه",
  "code": "acme",
  "nationalId": "10101234567",
  "insuranceWorkshopCode": "1234567890",
  "economicCode": "411111111111",
  "iban": "IR820540102680020817909002",
  "address": "تهران، خیابان ...",
  "phone": "02112345678",
  "active": true,
  "createdAt": "2026-07-28T10:00:00Z",
  "adminUserId": 42,
  "adminUsername": "acme.admin"
}
```

`adminUserId` و `adminUsername` **فقط در همین پاسخ اولیه** پر می‌شوند — در `GET /api/v1/admin/tenants/{id}` بعدی نیستند.

خطاها:

| status | code                        | معنی                                      |
|--------|-----------------------------|--------------------------------------------|
| 400    | `error.validation`          | داده نامعتبر (رمز/شبا/طول).                |
| 409    | `tenant.code.duplicate`     | `code` تکراری.                              |
| 409    | `user.username.duplicate`   | `adminUsername` تکراری.                    |
| 409    | `user.email.duplicate`      | `adminEmail` تکراری.                       |

### 6.1 `GET /api/v1/admin/tenants` — لیست شرکت‌ها
پارامترها: `?page=0&size=20&sort=createdAt,desc`. پاسخ `PageResponse<TenantResponse>`.

### 6.2 `GET /api/v1/admin/tenants/{id}` — جزئیات یک شرکت
پاسخ `TenantResponse` (بدون `adminUserId`/`adminUsername`).

### 6.3 `PUT /api/v1/admin/tenants/{id}` — ویرایش
Body مثل `CreateTenantRequest` منهای فیلدهای admin و `code`. همه اختیاری (partial update).

### 6.4 `POST /api/v1/admin/tenants/{id}/deactivate` / `.../activate`
غیرفعال کردن شرکت: همه‌ی کاربران آن شرکت بلافاصله از login محروم می‌شوند (پیام `auth.tenant_inactive`). داده حذف نمی‌شود.

### 6.5 مدیریت کاربران یک شرکت — `/api/v1/admin/tenants/{tenantId}/users/*`

مسیر نجات وقتی شرکت مدیر فعالی ندارد.

#### `GET /api/v1/admin/tenants/{tenantId}/users`
مثل `/api/v1/users` ولی شناسه شرکت در URL. پارامترهای صفحه‌بندی یکسان.

#### `POST /api/v1/admin/tenants/{tenantId}/users`
Body و پاسخ عیناً مثل `POST /api/v1/users`، با یک تفاوت: **`COMPANY_ADMIN` هم قابل تخصیص است**. فقط `SUPER_ADMIN` مجاز نیست. رمز اولیه سرور تولید می‌کند و در پاسخ `initialPassword` برمی‌گردد.

#### `POST /api/v1/admin/tenants/{tenantId}/users/{userId}/reset-password`
همان reset-password ولی هر کاربر هر شرکتی. پاسخ `{ "initialPassword": "..." }`.

#### `POST /api/v1/admin/tenants/{tenantId}/users/{userId}/grant-company-admin`
افزودن نقش `COMPANY_ADMIN` به کاربر موجود (نقش‌های قبلی حفظ می‌شوند). حساب فعال هم می‌شود. کاربردی برای وقتی مدیر قبلی رفته و باید کسی جایگزین شود.
پاسخ `200` با `UserResponse`. خطای `409 user.already_company_admin` اگر از قبل مدیر شرکت باشد.

---

## 7) اطلاعات شرکت جاری — `/api/v1/my-company` (نه دقیقاً user، ولی مرتبط با پروفایل شرکت)

- `GET /api/v1/my-company` (`SETTINGS_READ`): اطلاعات شرکتی که کاربر جاری به آن تعلق دارد.
- `PUT /api/v1/my-company` (`SETTINGS_WRITE`): ویرایش partial (فقط فیلدهای ارسال‌شده). کد شرکت و فعال/غیرفعال از این مسیر قابل تغییر نیستند.

---

## 8) جریان‌های کاربردی (UX Flows) که فرانت باید بسازد

### 8.0 Onboarding مشتری جدید (توسط SUPER_ADMIN)
این جریان اصلی SUPER_ADMIN است — فرم «افزودن مشتری جدید» در پنل ادمین.
1. SUPER_ADMIN وارد پنل شده (پس `me.roles` شامل `SUPER_ADMIN` و `tenantId=null`).
2. UI فرم «ثبت مشتری جدید» را نشان می‌دهد: دو بخش — «اطلاعات شرکت» و «اطلاعات مدیر شرکت» + فیلد «رمز اولیه».
3. Submit → `POST /api/v1/admin/tenants` با تمام فیلدها.
4. اگر 409 → toast مشخص‌کننده کدام یکتایی نقض شده (کد شرکت / نام کاربری / ایمیل مدیر).
5. اگر 201 → مودال موفقیت با اطلاعات مدیر:
   - نام کاربری مدیر (از پاسخ `adminUsername`).
   - رمز اولیه (همان که فرانت به سرور فرستاده — سرور آن را echo نمی‌کند، پس فرانت باید خودش نگه‌داشته باشد).
   - دکمه «کپی اعتبارنامه» یا «پرینت» تا SUPER_ADMIN امن به مشتری تحویل دهد.
   - پیام «مدیر پس از اولین ورود باید رمز را عوض کند.»
   - پیام «یک کد تأیید ایمیل هم به ایمیل مدیر ارسال شد — بعد از اولین ورود باید ایمیلش را از داخل پنل تأیید کند تا مسیر بازیابی رمز فعال شود.»
6. ریدایرکت به `GET /api/v1/admin/tenants/{id}` برای دیدن جزئیات + امکان اضافه‌کردن کاربران بیشتر از `POST /api/v1/admin/tenants/{tenantId}/users`.

### 8.1 ورود اولیه‌ی کاربر جدید
1. مدیر شرکت با `POST /users` کاربر ساخت و initialPassword گرفت.
2. کاربر با آن رمز از `POST /auth/login` وارد شد.
3. فرانت روی response بعد از هر login **flag بررسی کن**: اگر `passwordChangedAt` قدیمی است یا رمز از سرور تولید شده (ما این نشانه را به‌طور خودکار نمی‌فرستیم — پیشنهاد: صفحه‌ی "welcome, please change your password" را دفعه اول اجباری کن و بعد `passwordChangedAt` را چک کن؛ اگر می‌خواهی می‌توانی فیلد `mustChangePassword` را در ورژن بعدی از بک‌اند بخواهی).
4. کاربر رمز را با `/auth/change-password` عوض کرد → همه‌ی sessionها revoke شد → دوباره login کن.

### 8.2 فراموشی رمز (OTP)
1. کاربر روی «فراموشی رمز» → صفحه‌ی ایمیل.
2. `POST /auth/forgot-password { email }` → پاسخ 204.
3. UI: «اگر این ایمیل ثبت و تأیید شده باشد، کد ۶ رقمی به آن ارسال می‌شود (اعتبار ۱۰ دقیقه).»
4. کاربر روی همان صفحه (یا صفحه بعدی) کد ۶ رقمی + رمز جدید را وارد می‌کند.
5. `POST /auth/reset-password { email, code, newPassword }`.
6. پاسخ 204 → «رمز شما عوض شد. لطفاً وارد شوید» → هدایت به /login. **همه sessionها revoke شده‌اند.**
7. اگر `401 otp.invalid` گرفتی → toast «کد نامعتبر است» + شمارنده تلاش (فرانت نمی‌داند چند تلاش مانده، ولی بعد از ۵ بار کد invalidate می‌شود و کاربر باید کد جدید بگیرد).

**UX پیشنهادی**: یک صفحه با سه فیلد (ایمیل، کد، رمز جدید) بهتر از دو صفحه — کاربر ایمیل را از قبل وارد کرده، فقط کد و رمز اضافه می‌کند.

### 8.2.1 تأیید ایمیل بعد از onboarding
1. کاربر تازه ساخته‌شده وارد پنل می‌شود؛ `me.emailVerified === false` است.
2. UI بنر بالای صفحه: «برای دسترسی کامل، ایمیلتان را تأیید کنید. کد به `admin@acme.com` ارسال شده.»
3. دکمه‌ی «ارسال مجدد کد» → `POST /auth/resend-verification` (rate limit ۵/دقیقه/IP).
4. فرم کوچک کد + دکمه «تأیید» → `POST /auth/verify-email { code }`.
5. پاسخ 204 → بنر ناپدید می‌شود، `me` را دوباره fetch کن یا `emailVerified` را در store به `true` تغییر بده.
6. اگر `401 otp.invalid` → toast «کد نامعتبر است» + پیشنهاد ارسال مجدد.

**نکته حیاتی**: تا قبل از تأیید ایمیل، `/forgot-password` برای این کاربر بی‌اثر است. اگر کاربر ایمیلش را فراموش کرده باشد و ایمیل هم تأیید نشده باشد، باید SUPER_ADMIN از `/api/v1/admin/tenants/{tenantId}/users/{userId}/reset-password` رمز را ریست کند.

### 8.3 تغییر رمز از داخل پنل
1. کاربر رفت به «تنظیمات» → «تغییر رمز».
2. سه فیلد: رمز فعلی، رمز جدید، تکرار رمز جدید (تکرار فقط client-side).
3. `POST /auth/change-password { currentPassword, newPassword }`.
4. پاسخ 204 → toast «رمز عوض شد، لطفاً دوباره وارد شوید» → clear tokens → /login.

### 8.4 لیست/ساخت کاربر توسط ادمین
1. `GET /users?page=0&size=20&sort=createdAt,desc` → جدول با ستون‌های: نام کاربری، نام و نام‌خانوادگی، ایمیل، نقش‌ها (chip)، وضعیت (فعال/غیرفعال/قفل)، آخرین ورود.
2. دکمه‌ی «افزودن کاربر»: فقط اگر `USER_WRITE` داری.
3. مودال ساخت با فیلدها + انتخاب چندگزینه‌ی نقش. لیست نقش‌های مجاز را از `GET /api/v1/roles` (اگر endpoint role وجود دارد) بگیر؛ در غیر این صورت hardcode: `[ACCOUNTANT, MANAGER, EMPLOYEE, AUDITOR]` + نقش‌های سفارشی شرکت.
4. بعد از موفقیت، مودال «رمز اولیه» با دکمه‌ی کپی و پیام «این رمز فقط یک بار نمایش داده می‌شود».

### 8.5 غیرفعال کردن کاربر
1. در ردیف جدول، دکمه‌ی سه‌نقطه → «غیرفعال کردن».
2. Confirm modal: «مطمئنی؟ کاربر دیگر نمی‌تواند وارد شود و همه‌ی نشست‌هایش قطع می‌شود.»
3. `POST /users/{id}/deactivate`. اگر 400 برگشت (self) → toast خطا. اگر 409 (already_disabled) → refresh لیست.

### 8.6 بازنشانی رمز کاربر
1. دکمه‌ی «بازنشانی رمز» فقط برای مدیر روی کاربرهای غیر از خودش.
2. Confirm: «رمز جدید تولید می‌شود و همه‌ی نشست‌های کاربر قطع می‌شود.»
3. `POST /users/{id}/reset-password` → مودال با رمز نو + کپی.

---

## 9) پیشنهادهای معماری فرانت

- **کِلاینت HTTP**: axios با یک interceptor خواندنی برای auth (بالا توضیح داده شد) + interceptor برای زدن `Accept-Language: fa` (اگر بک‌اند locale-aware شد در آینده).
- **State**: react-query / TanStack Query برای cache و invalidation. `queryKey: ["users", { page, size }]`.
- **Store سبک**: zustand یا context برای user اکنون + tokens. بعد از login → `setAuth({ user, access, refresh })`.
- **Route guard**: HOC یا layout wrapper که چک کند `me` بارگذاری شده و لازم permissionها را دارد؛ در غیر این صورت redirect به /login یا /forbidden.
- **RTL**: کل پنل RTL، فارسی. برای اعداد از `toLocaleString("fa-IR")` استفاده کن.
- **Form**: react-hook-form + zod. schema رمز باید با policy بک‌اند هماهنگ باشد (min 8، حداقل یک حرف و یک رقم).
- **Toast**: sonner یا react-hot-toast. رنگ خطا برای `status >= 400`، رنگ سبز برای success.

---

## 10) نکات امنیتی که فرانت باید رعایت کند

1. هرگز `password` را در URL/query نگذار.
2. `initialPassword` را در URL نگذار (فقط در حالت داخلی modal).
3. توکن‌ها را به هیچ third-party (Sentry, LogRocket, ...) نفرست.
4. روی logout همه‌ی storage را پاک کن + `refreshToken` را حتماً به `/auth/logout` بفرست تا server-side هم revoke شود.
5. روی `401` بعد از refresh ناموفق، **حتماً** state احراز را پاک کن — اگر ادمین حساب را غیرفعال کرد، ادامه‌ی درخواست‌ها بی‌فایده است.
6. CORS از سمت بک‌اند فقط origins مشخص را می‌پذیرد. اگر در دِو صد `Origin` خطا گرفتی، به بک‌اند بگو origin دِو (`http://localhost:5173` یا هرچه) به `app.security.cors.allowed-origins` اضافه شود.

---

## 11) چک‌لیست تحویل فرانت

- [ ] صفحه login کار می‌کند و به داشبورد هدایت می‌کند.
- [ ] Interceptor auto-refresh کار می‌کند (تست: بعد از ۱۵ دقیقه بی‌کاری، اولین کلیک باید بدون logout عبور کند).
- [ ] logout واقعاً refreshToken را revoke می‌کند (چک با login دوباره + استفاده از refresh قدیمی → باید 401 بگیرد).
- [ ] change-password → بعدش کاربر به login فرستاده می‌شود.
- [ ] forgot-password + reset-password end-to-end با یک ایمیل دِو (log سرور را ببین).
- [ ] لیست کاربران با pagination + sort کار می‌کند.
- [ ] ساخت کاربر → مودال initialPassword نمایش داده می‌شود.
- [ ] ویرایش کاربر → username غیرقابل تغییر است (readonly).
- [ ] Deactivate/Activate/Reset-password دکمه‌ها فقط با `USER_WRITE` نشان داده می‌شوند.
- [ ] پیام‌های خطا فارسی و از `message` گرفته می‌شوند.
- [ ] Rate limit (429) toast مناسب.
- [ ] RTL و فونت فارسی درست.

---

## 11.5) Audit Logs — `/api/v1/audit-logs/*`

مشاهده تاریخچه‌ی رویدادها برای ممیزی. audit logs **immutable** هستند (append-only trigger روی DB) — فرانت فقط GET دارد.

### قوانین دسترسی
- **`/api/v1/audit-logs`** — دسترسی `AUDIT_LOG_READ` (`COMPANY_ADMIN` و `AUDITOR` دارن). فقط رویدادهای شرکت جاری. SUPER_ADMIN نمی‌تواند اینجا صدا بزند (400 `audit.tenant_required`).
- **`/api/v1/admin/audit-logs`** — دسترسی `TENANT_READ` (SUPER_ADMIN). دید cross-tenant + رویدادهای پلتفرمی (که `tenantId=null` دارند مثل login SUPER_ADMIN).

### `GET /api/v1/audit-logs`

Query params (همه اختیاری):

| param     | نوع              | مثال                     |
|-----------|-------------------|--------------------------|
| `event`   | string             | `LOGIN`, `USER_CREATED`  |
| `outcome` | enum               | `SUCCESS \| FAILURE \| DENIED` |
| `userId`  | long               | `42`                     |
| `from`    | ISO-8601 Instant   | `2026-07-01T00:00:00Z`   |
| `to`      | ISO-8601 Instant   | `2026-08-01T00:00:00Z`   |
| `page`    | int (0-based)      | `0`                      |
| `size`    | int                | `50` (پیش‌فرض)            |
| `sort`    | string             | `createdAt,desc` (پیش‌فرض)|

پاسخ `PageResponse<AuditLogResponse>`:

```json
{
  "content": [
    {
      "id": 1234,
      "tenantId": 7,
      "userId": 42,
      "username": "acme.admin",
      "event": "USER_CREATED",
      "outcome": "SUCCESS",
      "targetType": "User",
      "targetId": "58",
      "details": "User 'acme.hesabdar' created in tenant 7 with roles=[ACCOUNTANT]",
      "ipAddress": "10.0.0.5",
      "userAgent": "Mozilla/5.0 ...",
      "createdAt": "2026-07-28T09:12:00Z"
    }
  ],
  "page": 0, "size": 50, "totalElements": 1240, "totalPages": 25
}
```

### `GET /api/v1/admin/audit-logs`

مثل بالا + یک param اضافه:

| param      | نوع  | معنی                                              |
|------------|-------|---------------------------------------------------|
| `tenantId` | long  | محدود کردن به یک شرکت خاص. اگر ندی، همه شرکت‌ها + پلتفرم. |

### لیست eventهای معتبر (برای autocomplete در فیلتر UI)

**Auth**: `LOGIN`, `LOGOUT`, `TOKEN_REFRESH`
**Password**: `PASSWORD_CHANGE`, `PASSWORD_RESET_REQUEST`, `PASSWORD_RESET_COMPLETE`
**Email**: `EMAIL_VERIFIED`
**User admin**: `USER_CREATED`, `USER_UPDATED`, `USER_ROLE_CHANGED`
**Roles**: `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `ROLE_PERMISSIONS_UPDATED`
**Project**: `PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_ARCHIVED`, `PROJECT_RESTORED`
**Employee**: `EMPLOYEE_CREATED`, `EMPLOYEE_UPDATED`, `EMPLOYEE_DEACTIVATED`, `EMPLOYEE_REACTIVATED`, `EMPLOYEE_TERMINATED`, `EMPLOYEE_DELETED`, `EMPLOYEE_REHIRED`
**Contract**: `CONTRACT_CREATED`, `CONTRACT_UPDATED`, `CONTRACT_ENDED`, `CONTRACT_VOIDED`
**Tenant**: `TENANT_CREATED`, `TENANT_UPDATED`, `TENANT_DEACTIVATED`, `TENANT_ACTIVATED`

### UX flow پیشنهادی برای صفحه Audit Logs
1. جدول با ستون‌های: زمان (فارسی + `toLocaleString('fa-IR')`), رویداد (chip رنگی: سبز=SUCCESS، قرمز=FAILURE، زرد=DENIED), کاربر (username + link به پروفایل), جزئیات (details, truncate با tooltip), IP.
2. فیلتر بالای جدول: date range picker + select event (لیست بالا) + select outcome + input userId (یا combo box با جست‌وجوی کاربر).
3. صفحه‌بندی server-side — رکوردها سریع زیاد می‌شن، هرگز کل داده رو نگیر.
4. برای SUPER_ADMIN، select tenant اضافه بشه (با جست‌وجو در `/api/v1/admin/tenants`).
5. رفتار خطا: `400 audit.tenant_required` یعنی SUPER_ADMIN دارد از مسیر شرکت‌محور استفاده می‌کند — به `/api/v1/admin/audit-logs` سوییچ کن.

---

## 12) محدودیت‌های CORS و امنیت مرورگر که فرانت باید بداند

بک‌اند هدرهای امنیتی سخت‌گیرانه‌ای می‌فرستد. مرورگر خودش اعمالشان می‌کند، ولی چند نکته که فرانت باید رعایت کند تا در CORS گیر نکند:

1. **`credentials: false`** — هرگز از `credentials: 'include'` در fetch/axios استفاده نکن. احراز فقط با `Authorization: Bearer` است، کوکی نداریم. اگر بگذاری، preflight fail می‌شود.
2. **هدرهای مجاز درخواست**: فقط `Authorization`, `Content-Type`, `Accept`, `X-Requested-With`. اگر هدر custom (مثل `X-Request-ID` یا `X-Client-Version`) می‌خواهی بفرستی، اول از بک‌اند بخواه به `app.security.cors.allowed-headers` اضافه کند — وگرنه preflight برمی‌گردد.
3. **هدرهای response قابل خواندن**: فقط `Content-Disposition` صریح expose شده. برای دانلود فایل که بخواهی نام فایل را از `Content-Disposition` بخوانی، کار می‌کند. سایر هدرهای response از JS قابل خواندن نیستند مگر بک‌اند expose کند.
4. **HTTPS اجباری در prod**: HSTS با `max-age=1year` + `includeSubDomains` تنظیم شده. اگر یک بار سایت روی HTTP باز شود، مرورگر تا یک سال روی HTTPS قفلش می‌کند. در prod حتماً همه‌ی assets و API روی HTTPS.
5. **Origin در allowlist**: بک‌اند wildcard قبول نمی‌کند. برای هر environment، origin دقیق فرانت باید در `app.security.cors.allowed-origins` باشد (مثلاً `http://localhost:5173` برای dev، `https://panel.manaz.pro` برای prod). اگر خطای CORS گرفتی، origin دِوت را به بک‌اند اعلام کن.
6. **API قابل iframe نیست**: `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'`. اگر می‌خواهی swagger یا صفحه‌ی خطا را در iframe نشان دهی، نمی‌شود — و نباید.
7. **CSRF token لازم نیست**: چون auth با Bearer header است نه کوکی، CSRF غیرفعال است. هیچ توکن CSRFی نگیر و نفرست.
8. **Preflight (OPTIONS)**: مرورگر خودش برای درخواست‌های cross-origin با هدر custom (مثل `Authorization`) preflight می‌فرستد. سرور همیشه اجازه می‌دهد. اگر می‌بینی OPTIONS برمی‌گردد `403`، مشکل از origin allowlist است نه چیز دیگر.

---

## 13) لینک‌های مفید

- Swagger UI بک‌اند: `http://localhost:8080/swagger-ui.html`  (ملاحظه: دلیل اینکه CSP دقیقاً `default-src 'self'` نیست، همینه که Swagger UI بشکنه نشود)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

اگر چیزی مبهم بود، اول Swagger UI را باز کن؛ همان چیزی است که این سند از رویش نوشته شده.
