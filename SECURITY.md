# نکات امنیتی / استقرار

## متغیرهای محیطی اجباری

اپ عمداً بدون این envها بالا نمی‌آید (fail-fast):

| نام | توضیح |
|---|---|
| `DB_URL` | مثلاً `jdbc:postgresql://db:5432/accounting_db` |
| `DB_USER` | کاربر PostgreSQL |
| `DB_PASSWORD` | رمز PostgreSQL |
| `JWT_SECRET` | Base64 با حداقل ۳۲ بایت decode شده. تولید: `openssl rand -base64 48` |
| `SUPER_ADMIN_PASSWORD` | فقط برای اولین boot استفاده می‌شود. اگر super-admin از قبل موجود باشد نادیده گرفته می‌شود. |

اختیاری:
| نام | پیش‌فرض | توضیح |
|---|---|---|
| `PORT` | 8080 | |
| `CORS_ENABLED` | false | |
| `CORS_ALLOWED_ORIGINS` | خالی | لیست کاما-جدا از originهای دقیق (بدون wildcard) |
| `LOG_LEVEL_APP` | INFO | برای debug روی `DEBUG` بگذارید — در prod نگذارید |
| `SUPER_ADMIN_ENABLED` | true | برای غیرفعال کردن seeder |

## پیش از استقرار prod

- [ ] `.env` ساخته شده و در git نیست (`.gitignore` جلویش را می‌گیرد)
- [ ] رمز DB واقعی است، نه مقدار پیش‌فرض تاریخی
- [ ] `JWT_SECRET` با `openssl rand -base64 48` تولید شده
- [ ] `SUPER_ADMIN_PASSWORD` قوی است و بعد از اولین ورود از پنل تعویض می‌شود
- [ ] app پشت reverse proxy TLS-terminating قرار دارد
- [ ] `CORS_ALLOWED_ORIGINS` روی دامنه واقعی پنل تنظیم شده
- [ ] Actuator prometheus `/actuator/prometheus` توسط proxy از دسترسی عمومی جدا شده (یا داخلی شبکه محدود شده)

## آنچه در این نسخه اضافه شده

- Rate limit بر پایه IP روی endpointهای auth (کانفیگ در `app.security.rate-limit`)
- Refresh token rotation اتمی — تلاش دوباره با یک توکن باعث revoke همه sessionهای کاربر می‌شود (سیگنال replay)
- هدرهای امنیتی مرورگر (HSTS, CSP سخت‌گیرانه, no-referrer, X-Content-Type-Options, DENY frames)
- Access token TTL از ۱۵ دقیقه به ۵ دقیقه کوتاه شد
- سقف صفحه‌بندی روی ۱۰۰ ردیف
- JWT secret ضعیف در startup رد می‌شود
- `flyway.clean` غیرفعال
- Actuator: `health`, `info` عمومی؛ `prometheus` هم عمومی است ولی توصیه می‌شود پشت proxy محدود شود؛ بقیه‌ی actuatorها نیاز به نقش `SUPER_ADMIN` دارند
- `open-in-view: false`

## چیزهایی که هنوز مسئولیت اپراتور است

- **چرخش secret‌های قدیمی که در git history هستند** — `git filter-repo` یا BFG. رمزهای عمومی که در تاریخچه بودند دیگر معتبر نباشند.
- **پشتیبان‌گیری منظم DB** و تست restore
- **TLS termination** توسط reverse proxy (nginx/caddy/liara)
- **Log aggregation** (Loki/ELK) — الان JSON structured نیست ولی از stdout قابل جمع‌آوری است
- **Alerting** بر مبنای متریک‌های Prometheus
- **Multi-instance rate limit**: پیاده‌سازی فعلی in-memory است؛ اگر پشت load balancer با چند نمونه رفتید، سقف مؤثر × N می‌شود. برای دقت واقعی، نسخه Redis-based لازم است.
