package com.accounting.security.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Standard Iranian national ID checksum algorithm:
 * <ol>
 *   <li>Must be exactly 10 digits.</li>
 *   <li>All-same-digit codes (0000000000, 1111111111, ...) are rejected.</li>
 *   <li>Sum digits[0..8] weighted by (10 - index), then mod 11.</li>
 *   <li>If remainder &lt; 2 → check digit must equal remainder.
 *       Otherwise → check digit must equal (11 - remainder).</li>
 * </ol>
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
        // Reject all-same-digit codes
        if (value.chars().distinct().count() == 1) {
            return false;
        }

        int check = value.charAt(9) - '0';
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (value.charAt(i) - '0') * (10 - i);
        }
        int remainder = sum % 11;
        return (remainder < 2) ? (check == remainder) : (check == 11 - remainder);
    }
}
