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

@Tag(name = "Projects", description = "مدیریت پروژه‌های شرکت")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    @Operation(
            summary = "لیست پروژه‌ها",
            description = """
            بازگرداندن لیست پروژه‌های شرکت جاری با صفحه‌بندی.
            به‌صورت پیش‌فرض فقط پروژه‌های فعال بازمی‌گردند.
            برای دیدن پروژه‌های آرشیو‌شده در کنار فعال‌ها، پارامتر includeArchived=true را ارسال کنید.
            پیش‌فرض صفحه‌بندی: size=20، مرتب‌سازی بر اساس createdAt نزولی.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست با موفقیت بازگردانده شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_READ ندارید")
    })
    public PageResponse<ProjectResponse> list(
            @Parameter(description = "نمایش پروژه‌های آرشیو‌شده در کنار فعال‌ها")
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return projectService.list(includeArchived, pageable);
    }

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
            description = "ویرایش نام و توضیحات پروژه. کد پروژه تغییرناپذیر است و در این endpoint قابل تغییر نیست."
    )
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

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "آرشیو پروژه",
            description = """
            آرشیو کردن پروژه (active=false). پروژه حذف نمی‌شود و
            سابقه قراردادهای مرتبط حفظ می‌ماند. برای بازگرداندن از endpoint restore استفاده کنید.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "پروژه با موفقیت آرشیو شد"),
            @ApiResponse(responseCode = "400", description = "پروژه از قبل آرشیو شده است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد"),
            @ApiResponse(responseCode = "409", description = "پروژه قراردادهای فعال دارد و قابل آرشیو نیست")
    })
    public void archive(@PathVariable Long id) {
        projectService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('PROJECT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "بازگرداندن پروژه از آرشیو",
            description = "پروژه‌ای که قبلاً آرشیو شده را به حالت فعال بازمی‌گرداند."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "پروژه با موفقیت بازگردانده شد"),
            @ApiResponse(responseCode = "400", description = "پروژه در حال حاضر آرشیو نشده است"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی PROJECT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد")
    })
    public void restore(@PathVariable Long id) {
        projectService.restore(id);
    }
}