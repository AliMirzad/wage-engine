package ir.manaz.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.audit.AuditLogDtos.AuditLogResponse;
import ir.manaz.audit.AuditLogQueryService.AuditLogFilter;
import ir.manaz.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Audit Logs", description = "مشاهده رویدادهای audit شرکت جاری — برای ممیزی و بازرسی")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService queryService;

    @Operation(
            summary = "جست‌وجو در audit logs شرکت جاری",
            description = """
                    شناسه شرکت از توکن خوانده می‌شود؛ SUPER_ADMIN باید از
                    /api/v1/admin/audit-logs استفاده کند. همه فیلترها اختیاری‌اند
                    و مرتب‌سازی همیشه نزولی روی createdAt است.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست رویدادها"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی AUDIT_LOG_READ ندارید")
    })
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @Parameter(description = "نام رویداد، مثلاً LOGIN یا USER_CREATED") @RequestParam(required = false) String event,
            @Parameter(description = "SUCCESS | FAILURE | DENIED") @RequestParam(required = false) AuditOutcome outcome,
            @Parameter(description = "شناسه کاربر انجام‌دهنده") @RequestParam(required = false) Long userId,
            @Parameter(description = "شروع بازه (inclusive) — ISO-8601 مانند 2026-07-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "پایان بازه (exclusive) — ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.searchForCurrentTenant(
                new AuditLogFilter(event, outcome, userId, from, to), pageable);
    }
}
