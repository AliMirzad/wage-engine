package ir.manaz.payroll.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.payroll.project.ProjectDtos.CreateProjectRequest;
import ir.manaz.common.PageResponse;
import ir.manaz.payroll.project.ProjectDtos.ProjectResponse;
import ir.manaz.payroll.project.ProjectDtos.UpdateProjectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static ir.manaz.payroll.project.ProjectDtos.*;

@Tag(name = "Projects", description = "مدیریت پروژه‌های شرکت")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    @Operation(summary = "جزئیات یک پروژه", description = "بازگرداندن اطلاعات یک پروژه بر اساس شناسه.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "پروژه یافت شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد")
    })
    public ProjectResponse getById(@PathVariable Long id) {
        return projectService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "ایجاد پروژه جدید",
            description = "ساخت پروژه جدید در شرکت جاری. کد پروژه باید در سطح شرکت یکتا باشد و پس از ایجاد قابل تغییر نیست."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "پروژه با موفقیت ساخته شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی ورودی"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_WRITE ندارید"),
            @ApiResponse(responseCode = "409", description = "کد پروژه تکراری است")
    })
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        return projectService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @Operation(
            summary = "ویرایش پروژه",
            description = """
            ویرایش پروژه. فقط فیلدهای ارسال‌شده به‌روز می‌شوند.
            کد پروژه و وضعیت از این مسیر قابل تغییر نیستند —
            برای تغییر وضعیت از POST /{id}/status استفاده کنید.
            مبلغ پیمان فقط توسط دارنده PROJECT_FINANCIAL_READ قابل ثبت است؛
            در غیر این صورت مقدار ارسالی نادیده گرفته می‌شود و مقدار موجود دست‌نخورده می‌ماند.
            """    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "پروژه با موفقیت ویرایش شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی ورودی"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد")
    })
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest req) {
        return projectService.update(id, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    @Operation(
            summary = "لیست پروژه‌ها",
            description = """
            بازگرداندن لیست پروژه‌های شرکت جاری با صفحه‌بندی.
            به‌صورت پیش‌فرض فقط پروژه‌های باز (PLANNED، ACTIVE، SUSPENDED) بازمی‌گردند.
            برای دیدن پروژه‌های خاتمه‌یافته و لغوشده، پارامتر includeClosed=true را ارسال کنید.
            مبلغ پیمان فقط برای دارندگان دسترسی PROJECT_FINANCIAL_READ پر می‌شود؛ برای بقیه null است.
            پیش‌فرض صفحه‌بندی: size=20، مرتب‌سازی بر اساس createdAt نزولی.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست با موفقیت بازگردانده شد"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_READ ندارید")
    })
    public PageResponse<ProjectResponse> list(
            @Parameter(description = "نمایش پروژه‌های خاتمه‌یافته و لغوشده در کنار بازها")
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return projectService.list(includeClosed, pageable);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @Operation(
            summary = "تغییر وضعیت پروژه",
            description = """
            گذارهای مجاز:
            PLANNED → ACTIVE, CANCELLED
            ACTIVE → SUSPENDED, COMPLETED, CANCELLED
            SUSPENDED → ACTIVE, COMPLETED, CANCELLED
            COMPLETED / CANCELLED → ACTIVE (بازگشایی در صورت ثبت اشتباه)

            ثبت قرارداد جدید فقط در وضعیت ACTIVE ممکن است.
            رفتن به COMPLETED یا CANCELLED در صورت وجود قرارداد فعال مسدود می‌شود (۴۰۹)
            و پیام خطا شماره قراردادهای مانع را فهرست می‌کند.
            بازگشایی، قراردادهای خاتمه‌یافته را برنمی‌گرداند.
            فیلد availableTransitions در پاسخ هر پروژه، گزینه‌های مجاز را برای نمایش در فرم مشخص می‌کند.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "وضعیت با موفقیت تغییر کرد"),
            @ApiResponse(responseCode = "400", description = "گذار وضعیت غیرمجاز است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد"),
            @ApiResponse(responseCode = "409", description = "وضعیت تکراری است یا پروژه قرارداد فعال دارد")
    })
    public ProjectResponse changeStatus(@PathVariable Long id,
                                        @Valid @RequestBody ChangeStatusRequest req) {
        return projectService.changeStatus(id, req);
    }
}