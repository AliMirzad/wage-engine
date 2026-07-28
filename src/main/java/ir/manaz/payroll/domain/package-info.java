/**
 * Domain layer محاسبه دستمزد.
 * <p>
 * تلاش می‌کند از JPA/Spring MVC مستقل باشد — گرچه فعلاً به {@code Contract}
 * (entity JPA) وابسته است تا تکرار داده نداشته باشیم. برای decoupling کامل،
 * یک {@code PayrollSnapshot} می‌تواند به‌جای {@code Contract} گرفته شود.
 *
 * <h2>ساختار</h2>
 * <ul>
 *   <li>{@link ir.manaz.payroll.domain.Money} — value object پول با ارز</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollPeriod} — بازه محاسبه</li>
 *   <li>{@link ir.manaz.payroll.domain.Attendance} — کارکرد کارمند</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollContext} — ورودی محاسبه</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollComponent} — یک قلم (درآمد یا کسر)</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollResult} — خروجی — mutable در حین ران، سپس sealed</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollRule} — قاعده محاسبه (interface)</li>
 *   <li>{@link ir.manaz.payroll.domain.PayrollCalculator} — orchestrator</li>
 * </ul>
 *
 * <h2>افزودن قاعده جدید</h2>
 * یک bean از {@code PayrollRule} با {@code @Component} بسازید. calculator
 * خودکار پیدا می‌کند و به ترتیب {@code order()} اجرا می‌کند.
 *
 * <h2>گستره فعلی</h2>
 * <ul>
 *   <li>حقوق پایه به نسبت کارکرد + کسر غیبت</li>
 *   <li>مزایای ثابت قرارداد (حق مسکن، خواربار، ...)</li>
 *   <li>اضافه‌کاری ۱.۴×</li>
 *   <li>کار در تعطیل ۱.۴×</li>
 *   <li>شب‌کاری ۰.۳۵ اضافه</li>
 *   <li>بیمه تأمین اجتماعی ۷٪ سهم کارگر</li>
 *   <li>مالیات حقوق پلکانی (پله‌ها از config)</li>
 * </ul>
 *
 * <h2>گستره خارج (feature-flag شده در آینده)</h2>
 * <ul>
 *   <li>عیدی و پاداش پایان سال</li>
 *   <li>سنوات و بازخرید مرخصی</li>
 *   <li>بیمه تکمیلی / بیمه بیکاری</li>
 *   <li>مالیات معافیت بگیرها (معلولان، آزادگان و …)</li>
 *   <li>حق اولاد ضربدر تعداد فرزندان</li>
 *   <li>سقف مشمول بیمه</li>
 * </ul>
 */
package ir.manaz.payroll.domain;
