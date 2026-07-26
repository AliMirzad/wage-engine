package ir.manaz.payroll.project;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.payroll.contract.ContractRepository;
import ir.manaz.payroll.project.ProjectDtos.*;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private static final String FINANCIAL_AUTHORITY = "PROJECT_FINANCIAL_READ";

    /** وضعیت‌هایی که در حالت پیش‌فرض لیست نمایش داده می‌شوند. */
    private static final Set<ProjectStatus> OPEN_STATUSES =
            EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.ACTIVE, ProjectStatus.SUSPENDED);

    private final ContractRepository contractRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<ProjectResponse> list(boolean includeClosed, Pageable pageable) {
        Long tenantId = requireTenantId();
        boolean financial = canSeeFinancial();
        Page<Project> page = includeClosed
                ? projectRepository.findByTenantId(tenantId, pageable)
                : projectRepository.findByTenantIdAndStatusIn(tenantId, OPEN_STATUSES, pageable);
        return PageResponse.of(page.map(p -> ProjectResponse.from(p, financial)));
    }

    public ProjectResponse getById(Long id) {
        return ProjectResponse.from(load(id), canSeeFinancial());
    }

    // ─── Mutations ───────────────────────────────────────────

    @Transactional
    public ProjectResponse create(CreateProjectRequest req) {
        Long tenantId = requireTenantId();
        String code = req.code().trim().toUpperCase();

        if (projectRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ConflictException("project.code.duplicate", code);
        }
        validateDates(req.startDate(), req.endDate());

        Project project = Project.builder()
                .tenantId(tenantId)
                .name(req.name().trim())
                .code(code)
                .description(req.description())
                .status(ProjectStatus.ACTIVE)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .clientName(req.clientName())
                .clientNationalId(req.clientNationalId())
                .clientContractNumber(req.clientContractNumber())
                .clientContractDate(req.clientContractDate())
                // مبلغ پیمان فقط توسط دارنده دسترسی مالی قابل ثبت است
                .contractAmount(canSeeFinancial() ? req.contractAmount() : null)
                .location(req.location())
                .notes(req.notes())
                .build();
        project = projectRepository.save(project);

        audit(AuditEvent.PROJECT_CREATED, project, "code=" + project.getCode());
        return ProjectResponse.from(project, canSeeFinancial());
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest req) {
        Project project = load(id);

        if (req.name() != null && !req.name().isBlank()) project.setName(req.name().trim());
        if (req.description() != null) project.setDescription(req.description());
        if (req.clientName() != null) project.setClientName(req.clientName());
        if (req.clientNationalId() != null) project.setClientNationalId(req.clientNationalId());
        if (req.clientContractNumber() != null) project.setClientContractNumber(req.clientContractNumber());
        if (req.clientContractDate() != null) project.setClientContractDate(req.clientContractDate());
        if (req.location() != null) project.setLocation(req.location());
        if (req.notes() != null) project.setNotes(req.notes());

        LocalDate start = req.startDate() != null ? req.startDate() : project.getStartDate();
        LocalDate end = req.endDate() != null ? req.endDate() : project.getEndDate();
        validateDates(start, end);
        project.setStartDate(start);
        project.setEndDate(end);

        // بدون PROJECT_FINANCIAL_READ مقدار ارسالی نادیده گرفته می‌شود تا مقدار موجود خراب نشود
        if (req.contractAmount() != null && canSeeFinancial()) {
            project.setContractAmount(req.contractAmount());
        }

        audit(AuditEvent.PROJECT_UPDATED, project, null);
        return ProjectResponse.from(project, canSeeFinancial());
    }

    /**
     * گذار وضعیت پروژه با اعتبارسنجی گذارهای مجاز.
     * رفتن به وضعیت نهایی (COMPLETED / CANCELLED) در صورت وجود قرارداد فعال بلاک می‌شود.
     */
    @Transactional
    public ProjectResponse changeStatus(Long id, ChangeStatusRequest req) {
        Long tenantId = requireTenantId();
        Project project = load(id);

        ProjectStatus from = project.getStatus();
        ProjectStatus to = req.status();

        if (from == to) {
            throw new ConflictException("project.status.unchanged", to.name());
        }

        if (!from.canTransitionTo(to)) {
            throw new BusinessException("project.status.transition_invalid", from.name(), to.name());
        }

        if (to.isTerminal()) {
            List<String> activeContracts = contractRepository
                    .findActiveContractNumbersByProject(tenantId, project.getId(), LocalDate.now());
            if (!activeContracts.isEmpty()) {
                throw new ConflictException("project.close.has_active_contracts",
                        activeContracts.size(), String.join("، ", activeContracts));
            }
            project.setActualEndDate(req.actualEndDate() != null ? req.actualEndDate() : LocalDate.now());
            project.setClosedAt(Instant.now());
            project.setClosedBy(currentUserId());
        } else {
            // بازگشایی — مهر خاتمه پاک می‌شود
            project.setActualEndDate(null);
            project.setClosedAt(null);
            project.setClosedBy(null);
        }

        project.setStatus(to);

        String event = to.isTerminal() ? AuditEvent.PROJECT_ARCHIVED : AuditEvent.PROJECT_RESTORED;
        String details = from.name() + " → " + to.name()
                + (req.reason() != null ? " (" + req.reason() + ")" : "");
        audit(event, project, details);

        return ProjectResponse.from(project, canSeeFinancial());
    }

    // ─── Helpers ─────────────────────────────────────────────

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("project.end_date.before_start");
        }
    }

    private Project load(Long id) {
        Long tenantId = requireTenantId();
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", id));
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // TODO: Deferred #17 — دسترسی SUPER_ADMIN به endpointهای tenant-scoped
            throw new BusinessException("project.tenant_required");
        }
        return tenantId;
    }

    /** آیا کاربر جاری اجازه دیدن/ثبت مبلغ پیمان را دارد؟ */
    private boolean canSeeFinancial() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(FINANCIAL_AUTHORITY::equals);
    }

    private AuthenticatedPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) ? p : null;
    }

    private Long currentUserId() {
        AuthenticatedPrincipal p = principal();
        return p == null ? null : p.userId();
    }

    private String currentUsername() {
        AuthenticatedPrincipal p = principal();
        return p == null ? null : p.username();
    }

    private void audit(String event, Project project, String details) {
        auditLogService.log(event, AuditOutcome.SUCCESS,
                project.getTenantId(), currentUserId(), currentUsername(),
                details, "PROJECT", String.valueOf(project.getId()));
    }
}