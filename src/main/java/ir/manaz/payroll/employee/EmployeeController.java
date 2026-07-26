package ir.manaz.payroll.employee;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.common.PageResponse;
import ir.manaz.payroll.employee.EmployeeDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Employees", description = "مدیریت کارمندان شرکت")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(
            summary = "لیست کارمندان",
            description = """
            فهرست کارمندان شرکت جاری با صفحه‌بندی.
            به‌صورت پیش‌فرض کارمندان ترک‌کار‌کرده نمایش داده نمی‌شوند؛
            برای دیدن آن‌ها includeTerminated=true بفرستید.
            با active=true فقط فعال‌ها و با active=false فقط غیرفعال‌ها بازمی‌گردند؛
            اگر ارسال نشود هر دو می‌آیند.
            search روی نام، نام خانوادگی، کد پرسنلی و کد ملی جستجو می‌کند.
            کارمندان حذف‌شده در هیچ حالتی بازنمی‌گردند.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست با موفقیت بازگردانده شد"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_READ ندارید")
    })
    public PageResponse<EmployeeResponse> list(
            @Parameter(description = "فیلتر فعال بودن — خالی یعنی هر دو")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "شامل کارمندان ترک‌کار‌کرده")
            @RequestParam(defaultValue = "false") boolean includeTerminated,
            @Parameter(description = "جستجو در نام، کد پرسنلی و کد ملی")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return employeeService.list(active, includeTerminated, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(summary = "جزئیات یک کارمند", description = "بازگرداندن اطلاعات کامل یک کارمند بر اساس شناسه.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "کارمند یافت شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد")
    })
    public EmployeeResponse getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "ایجاد کارمند جدید",
            description = """
            ثبت کارمند جدید در شرکت جاری. کد پرسنلی به‌صورت خودکار
            با الگوی EMP-{tenantId}-{seq} تولید می‌شود و در ورودی نباید ارسال شود.
            کد ملی باید در سطح شرکت یکتا باشد (بین کارمندان حذف‌نشده).
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "کارمند با موفقیت ساخته شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی ورودی (تاریخ نامعتبر، کد ملی نامعتبر و غیره)"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "409", description = "کد ملی تکراری است")
    })
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest req) {
        return employeeService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @Operation(
            summary = "ویرایش اطلاعات کارمند",
            description = """
            ویرایش اطلاعات کارمند. فقط فیلدهای ارسال‌شده به‌روز می‌شوند؛
            فیلدهای ارسال‌نشده دست‌نخورده می‌مانند.
            کد ملی و کد پرسنلی تغییرناپذیر هستند.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "کارمند با موفقیت ویرایش شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی ورودی"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد")
    })
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest req) {
        return employeeService.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "غیرفعال‌سازی کارمند",
            description = """
            غیرفعال کردن موقت کارمند (مثلاً مرخصی طولانی، تعلیق).
            برخلاف حذف، این عملیات قابل بازگشت است و قراردادهای فعال کارمند
            دست‌نخورده می‌مانند. برای بازگرداندن از endpoint reactivate استفاده کنید.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "کارمند با موفقیت غیرفعال شد"),
            @ApiResponse(responseCode = "400", description = "کارمند از قبل غیرفعال است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد")
    })
    public void deactivate(@PathVariable Long id) {
        employeeService.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "فعال‌سازی مجدد کارمند",
            description = "بازگرداندن کارمند غیرفعال به حالت فعال."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "کارمند با موفقیت فعال شد"),
            @ApiResponse(responseCode = "400", description = "کارمند از قبل فعال است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد")
    })
    public void reactivate(@PathVariable Long id) {
        employeeService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "حذف کارمند (soft-delete)",
            description = """
            حذف نرم کارمند. رکورد در دیتابیس باقی می‌ماند اما از تمام
            queryهای عادی فیلتر می‌شود. سابقه برای audit حفظ می‌شود.
            
            **این عملیات از طریق API قابل بازگشت نیست.**
            
            اگر کارمند قرارداد فعال داشته باشد، حذف مسدود می‌شود (409)
            و پیام خطا شامل لیست شماره‌قراردادهای فعال است.
            برای اقامت موقت از deactivate استفاده کنید.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "کارمند با موفقیت حذف شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_DELETE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد"),
            @ApiResponse(responseCode = "409", description = "کارمند قرارداد فعال دارد و قابل حذف نیست")
    })
    public void delete(@PathVariable Long id) {
        employeeService.delete(id);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @Operation(
            summary = "ثبت ترک کار کارمند",
            description = """
            ثبت پایان همکاری (استعفا، اخراج، پایان قرارداد).
            برخلاف حذف، رکورد کارمند باقی می‌ماند چون برای محاسبه سنوات،
            تسویه‌حساب و سوابق بیمه لازم است. کارمند به‌طور خودکار غیرفعال می‌شود.

            اگر قرارداد فعالی وجود داشته باشد مسدود می‌شود (۴۰۹) و پیام خطا
            شامل شماره قراردادهای مانع است — ابتدا باید آن‌ها خاتمه یابند.

            برای بازگشت کارمند از endpoint rehire استفاده کنید، نه reactivate.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ترک کار با موفقیت ثبت شد"),
            @ApiResponse(responseCode = "400", description = "تاریخ ترک کار پیش از تاریخ استخدام است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد"),
            @ApiResponse(responseCode = "409", description = "قبلاً ترک کار ثبت شده یا کارمند قرارداد فعال دارد")
    })
    public EmployeeResponse terminate(@PathVariable Long id,
                                      @Valid @RequestBody TerminateEmployeeRequest req) {
        return employeeService.terminate(id, req);
    }

    @PostMapping("/{id}/rehire")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    @Operation(
            summary = "استخدام مجدد کارمند",
            description = """
            بازگرداندن کارمندی که قبلاً ترک کار کرده، با تاریخ استخدام جدید.
            رکورد جدید ساخته نمی‌شود — همان پرونده با کد پرسنلی قبلی ادامه می‌یابد،
            چون سابقه بیمه به شخص وابسته است نه به رکورد.

            تاریخ استخدام قبلی جایگزین می‌شود (طبق قانون کار، با تسویه سنوات
            هنگام ترک کار سابقه صفر می‌شود) ولی در audit log ثبت می‌ماند.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "استخدام مجدد با موفقیت ثبت شد"),
            @ApiResponse(responseCode = "400", description = "تاریخ استخدام جدید پیش از تاریخ ترک کار یا در آینده است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد"),
            @ApiResponse(responseCode = "409", description = "این کارمند ترک کار نکرده است")
    })
    public EmployeeResponse rehire(@PathVariable Long id,
                                   @Valid @RequestBody RehireEmployeeRequest req) {
        return employeeService.rehire(id, req);
    }
}