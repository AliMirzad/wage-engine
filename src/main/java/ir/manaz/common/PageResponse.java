package ir.manaz.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * پاسخ عمومی صفحه‌بندی‌شده برای همه endpointهای لیستی.
 */
@Schema(description = "پاسخ صفحه‌بندی‌شده")
public record PageResponse<T>(
        @Schema(description = "محتوای صفحه") List<T> content,
        @Schema(description = "شماره صفحه (صفر-پایه)", example = "0") int page,
        @Schema(description = "اندازه صفحه", example = "20") int size,
        @Schema(description = "کل رکوردها", example = "137") long totalElements,
        @Schema(description = "کل صفحات", example = "7") int totalPages
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(
                p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages()
        );
    }
}