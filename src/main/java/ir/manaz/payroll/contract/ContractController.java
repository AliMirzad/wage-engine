package ir.manaz.payroll.contract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.common.PageResponse;
import ir.manaz.payroll.contract.ContractDtos.*;
import ir.manaz.payroll.employee.EmployeeDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Contracts", description = "مدیریت قراردادهای کاری کارمندان با پروژه‌ها")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping("/contracts")
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    @Operation(summary = "لیست تمام قراردادها", description = "تمام قراردادهای شرکت جاری با صفحه‌بندی.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست بازگردانده شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_READ ندارید")
    })
    public PageResponse<ContractResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return contractService.list(pageable);
    }

    @GetMapping("/employees/{employeeId}/contracts")
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    @Operation(summary = "قراردادهای یک کارمند", description = "لیست تمام قراردادهای یک کارمند (فعال، پایان‌یافته، باطل‌شده).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست بازگردانده شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یافت نشد")
    })
    public PageResponse<ContractResponse> listByEmployee(
            @PathVariable Long employeeId,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return contractService.listByEmployee(employeeId, pageable);
    }

    @GetMapping("/projects/{projectId}/contracts")
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    @Operation(summary = "قراردادهای یک پروژه", description = "لیست تمام قراردادهای یک پروژه.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست بازگردانده شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد")
    })
    public PageResponse<ContractResponse> listByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return contractService.listByProject(projectId, pageable);
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('CONTRACT_READ')")
    @Operation(summary = "جزئیات یک قرارداد")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "قرارداد یافت شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "قرارداد یافت نشد")
    })
    public ContractResponse getById(@PathVariable Long id) {
        return contractService.getById(id);
    }

    @PostMapping("/contracts")
    @PreAuthorize("hasAuthority('CONTRACT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "ایجاد قرارداد جدید",
            description = """
            ایجاد قرارداد جدید برای یک (کارمند، پروژه). شماره قرارداد سرور تولید می‌کند
            با الگوی CT-{tenantId}-{seq}.
            
            محدودیت‌ها:
            - کارمند باید فعال و حذف‌نشده باشد.
            - پروژه نباید آرشیو شده باشد.
            - قرارداد فعال متداخل برای همان (کارمند، پروژه) نباید وجود داشته باشد.
            - تاریخ شروع نمی‌تواند بیش از ۵ سال در گذشته باشد.
            - اگر previousContractId ارسال شود، قرارداد قبلی باید end شده باشد
              و قبلاً به قرارداد دیگری متصل نشده باشد.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "قرارداد با موفقیت ساخته شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی (تاریخ نامعتبر، کارمند غیرفعال، پروژه آرشیو، مبلغ صفر)"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کارمند یا پروژه یا قرارداد قبلی یافت نشد"),
            @ApiResponse(responseCode = "409", description = "قرارداد متداخل وجود دارد یا قرارداد قبلی قبلاً لینک شده")
    })
    public ContractResponse create(@Valid @RequestBody CreateContractRequest req) {
        return contractService.create(req);
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('CONTRACT_WRITE')")
    @Operation(
            summary = "ویرایش قرارداد (فقط notes و terms)",
            description = """
            **تنها فیلدهای notes و terms قابل تغییرند.**
            
            برای تغییر مبلغ حقوق یا تاریخ‌ها، این قرارداد را با endpoint /end پایان دهید
            سپس یک قرارداد جدید بسازید و previousContractId این قرارداد را ارسال کنید.
            این کار الزام قانونی برای حفظ سابقه است.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "قرارداد ویرایش شد"),
            @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "قرارداد یافت نشد")
    })
    public ContractResponse update(@PathVariable Long id, @Valid @RequestBody UpdateContractRequest req) {
        return contractService.update(id, req);
    }

    @PostMapping("/contracts/{id}/end")
    @PreAuthorize("hasAuthority('CONTRACT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "پایان طبیعی قرارداد",
            description = "تنظیم تاریخ پایان روی قرارداد. قرارداد باطل یا قبلاً پایان‌یافته را نمی‌توان دوباره end کرد."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "قرارداد با موفقیت پایان یافت"),
            @ApiResponse(responseCode = "400", description = "قرارداد از قبل پایان‌یافته یا باطل شده، یا تاریخ نامعتبر"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "قرارداد یافت نشد")
    })
    public void end(@PathVariable Long id, @Valid @RequestBody EndContractRequest req) {
        contractService.end(id, req);
    }

    @PostMapping("/contracts/{id}/void")
    @PreAuthorize("hasAuthority('CONTRACT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "ابطال قرارداد (اشتباه ثبت)",
            description = """
            ابطال قرارداد به‌دلیل خطای داده‌ای. قرارداد باطل‌شده از تمام محاسبات payroll
            خارج می‌شود اما رکورد آن برای audit باقی می‌ماند.
            
            **دلیل ابطال الزامی است** — بعداً در گزارش‌های audit ثبت خواهد شد.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "قرارداد باطل شد"),
            @ApiResponse(responseCode = "400", description = "قرارداد قبلاً باطل شده"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی CONTRACT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "قرارداد یافت نشد")
    })
    public void voidContract(@PathVariable Long id, @Valid @RequestBody VoidContractRequest req) {
        contractService.voidContract(id, req);
    }

    @GetMapping("/api/v1/projects/{projectId}/employees")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(
            summary = "کارگران یک پروژه",
            description = """
            فهرست کارمندانی که روی این پروژه قرارداد دارند، به‌همراه اطلاعات
            قرارداد همان پروژه (شماره قرارداد، سمت، نوع، حقوق پایه، بازه تاریخی).

            کارمند مستقیماً به پروژه وصل نیست — اتصال از راه قرارداد است.
            یک کارمند ممکن است هم‌زمان روی چند پروژه قرارداد داشته باشد.

            به‌صورت پیش‌فرض فقط قراردادهای فعال امروز بازمی‌گردند و هر کارمند
            حداکثر یک ردیف دارد. با includeFormer=true قراردادهای خاتمه‌یافته
            هم می‌آیند و هر ردیف یک دوره همکاری است.
            قراردادهای باطل‌شده در هیچ حالتی بازنمی‌گردند.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "فهرست کارگران پروژه"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی EMPLOYEE_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "پروژه یافت نشد")
    })
    public PageResponse<ProjectEmployeeResponse> listEmployeesByProject(
            @PathVariable Long projectId,
            @Parameter(description = "شامل قراردادهای خاتمه‌یافته")
            @RequestParam(defaultValue = "false") boolean includeFormer,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return contractService.listEmployeesByProject(projectId, includeFormer, pageable);
    }
}