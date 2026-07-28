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

@Tag(name = "Admin - Audit Logs",
        description = "دید cross-tenant به audit logs — فقط SUPER_ADMIN")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogQueryService queryService;

    @Operation(
            summary = "جست‌وجو در audit logs کل پلتفرم",
            description = """
                    برخلاف /api/v1/audit-logs که فقط شرکت جاری را می‌بیند، این
                    مسیر روی همه‌ی شرکت‌ها + رویدادهای پلتفرمی (که tenantId=null دارند)
                    کار می‌کند. با فیلتر tenantId می‌توان به یک شرکت محدود کرد.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست رویدادها"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_READ ندارید")
    })
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @Parameter(description = "محدود کردن به یک شرکت خاص") @RequestParam(required = false) Long tenantId,
            @Parameter(description = "نام رویداد") @RequestParam(required = false) String event,
            @Parameter(description = "SUCCESS | FAILURE | DENIED") @RequestParam(required = false) AuditOutcome outcome,
            @Parameter(description = "شناسه کاربر انجام‌دهنده") @RequestParam(required = false) Long userId,
            @Parameter(description = "شروع بازه (inclusive) — ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "پایان بازه (exclusive) — ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.searchAcrossTenants(tenantId,
                new AuditLogFilter(event, outcome, userId, from, to), pageable);
    }
}
