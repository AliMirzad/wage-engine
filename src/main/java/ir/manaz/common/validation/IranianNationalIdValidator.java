package ir.manaz.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Lightweight national-id shape check (aligned with the SPA):
 * <ol>
 *   <li>Must be exactly 10 digits.</li>
 *   <li>All-same-digit codes (0000000000, 1111111111, 9999999999, …) are rejected.</li>
 * </ol>
 * Checksum / control-digit validation is intentionally not enforced.
 */
public class IranianNationalIdValidator implements ConstraintValidator<ValidIranianNationalId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true; // null handled by @NotBlank / @NotNull if required
        }
        if (!value.matches("\\d{10}")) {
            return false;
        }
        return value.chars().distinct().count() > 1;
    }
}
