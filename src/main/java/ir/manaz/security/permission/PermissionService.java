package ir.manaz.security.permission;

import ir.manaz.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository repo;

    public List<PermissionDefinition> listAll() {
        return repo.findAllByOrderByCategoryAscCodeAsc();
    }

    public PermissionDefinition getByCode(String code) {
        return repo.findByCode(code)
                .orElseThrow(() -> new NotFoundException("permission.not_found", code));
    }

    @Transactional
    public PermissionDefinition updateMetadata(String code, String descriptionFa, String category) {
        var p = getByCode(code);
        if (descriptionFa != null && !descriptionFa.isBlank()) {
            p.setDescriptionFa(descriptionFa.trim());
        }
        if (category != null && !category.isBlank()) {
            p.setCategory(category.trim());
        }
        return repo.save(p);
    }
}
