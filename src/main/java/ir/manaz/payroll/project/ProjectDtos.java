package ir.manaz.payroll.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

/**
 * تمام DTO های مرتبط با پروژه — همه به صورت record و immutable.
 */
public final class ProjectDtos {

    private ProjectDtos() {}

    @Schema(description = "درخواست ایجاد پروژه جدید")
    public record CreateProjectRequest(
            @Schema(description = "نام پروژه", example = "پروژه ساختمان تهران", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255)
            String name,

            @Schema(
                    description = "کد یکتای پروژه در سطح شرکت (فقط حروف/عدد/خط تیره/زیرخط)",
                    example = "P-001",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "{project.code.invalid_format}")
            String code,

            @Schema(description = "توضیحات پروژه (اختیاری)", example = "پروژه اجرای فاز اول ساختمان")
            @Size(max = 4000)
            String description
    ) {}

    @Schema(description = "درخواست ویرایش پروژه — کد قابل تغییر نیست")
    public record UpdateProjectRequest(
            @Schema(description = "نام جدید پروژه", example = "پروژه ساختمان تهران - فاز ۱", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255)
            String name,

            @Schema(description = "توضیحات جدید پروژه (اختیاری)")
            @Size(max = 4000)
            String description
    ) {}

    @Schema(description = "اطلاعات کامل یک پروژه")
    public record ProjectResponse(
            @Schema(description = "شناسه پروژه", example = "42") Long id,
            @Schema(description = "نام پروژه", example = "پروژه ساختمان تهران") String name,
            @Schema(description = "کد یکتای پروژه", example = "P-001") String code,
            @Schema(description = "توضیحات") String description,
            @Schema(description = "فعال یا آرشیو‌شده") boolean active,
            @Schema(description = "زمان آرشیو (اگر آرشیو شده)") Instant archivedAt,
            @Schema(description = "زمان ایجاد") Instant createdAt,
            @Schema(description = "زمان آخرین ویرایش") Instant updatedAt
    ) {
        public static ProjectResponse from(Project p) {
            return new ProjectResponse(
                    p.getId(), p.getName(), p.getCode(), p.getDescription(),
                    p.isActive(), p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }
}