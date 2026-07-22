package ir.manaz.payroll.project;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.payroll.contract.ContractRepository;
import ir.manaz.payroll.project.ProjectDtos.CreateProjectRequest;
import ir.manaz.common.PageResponse;
import ir.manaz.payroll.project.ProjectDtos.ProjectResponse;
import ir.manaz.payroll.project.ProjectDtos.UpdateProjectRequest;
import ir.manaz.security.user.CustomUserDetails;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ContractRepository contractRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<ProjectResponse> list(boolean includeArchived, Pageable pageable) {
        Long tenantId = requireTenantId();
        Page<Project> page = includeArchived
                ? projectRepository.findByTenantId(tenantId, pageable)
                : projectRepository.findByTenantIdAndActive(tenantId, true, pageable);
        return PageResponse.of(page.map(ProjectResponse::from));
    }

    public ProjectResponse getById(Long id) {
        Long tenantId = requireTenantId();
        return projectRepository.findByIdAndTenantId(id, tenantId)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new NotFoundException("project.not_found", id));
    }

    // ─── Mutations ───────────────────────────────────────────

    @Transactional
    public ProjectResponse create(CreateProjectRequest req) {
        Long tenantId = requireTenantId();

        if (projectRepository.existsByTenantIdAndCode(tenantId, req.code())) {
            throw new ConflictException("project.code.duplicate", req.code());
        }

        Project project = Project.builder()
                .tenantId(tenantId)
                .name(req.name())
                .code(req.code())
                .description(req.description())
                .active(true)
                .build();
        project = projectRepository.save(project);

        audit(AuditEvent.PROJECT_CREATED, project, "code=" + project.getCode());
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest req) {
        Long tenantId = requireTenantId();
        Project project = projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", id));

        project.setName(req.name());
        project.setDescription(req.description());

        audit(AuditEvent.PROJECT_UPDATED, project, null);
        return ProjectResponse.from(project);
    }

    @Transactional
    public void archive(Long id) {
        Long tenantId = requireTenantId();
        Project project = projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", id));

        if (!project.isActive()) {
            throw new BusinessException("project.already_archived");
        }

        List<String> activeContracts = contractRepository
                .findActiveContractNumbersByProject(tenantId, project.getId(), LocalDate.now());

        if (!activeContracts.isEmpty()) {
            String numbers = String.join("، ", activeContracts);
            throw new ConflictException(
                    "project.archive.has_active_contracts",
                    activeContracts.size(),
                    numbers
            );
        }

        project.setActive(false);
        project.setArchivedAt(Instant.now());
        project.setArchivedBy(currentUserId());

        audit(AuditEvent.PROJECT_ARCHIVED, project, null);
    }

    @Transactional
    public void restore(Long id) {
        Long tenantId = requireTenantId();
        Project project = projectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", id));

        if (project.isActive()) {
            throw new BusinessException("project.not_archived");
        }

        project.setActive(true);
        project.setArchivedAt(null);
        project.setArchivedBy(null);

        audit(AuditEvent.PROJECT_RESTORED, project, null);
    }

    // ─── Helpers ─────────────────────────────────────────────

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // TODO: وقتی SUPER_ADMIN بخواد به projects دسترسی مستقیم داشته باشه، اینجا رفتار متفاوت باشه
            throw new BusinessException("project.tenant_required");
        }
        return tenantId;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private void audit(String event, Project project, String details) {
        auditLogService.log(
                event,
                AuditOutcome.SUCCESS,
                project.getTenantId(),
                currentUserId(),
                currentUsername(),
                details,
                "PROJECT",
                String.valueOf(project.getId())
        );
    }
}