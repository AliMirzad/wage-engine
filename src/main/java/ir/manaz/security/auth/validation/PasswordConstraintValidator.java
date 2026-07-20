package ir.manaz.security.auth.validation;

import ir.manaz.config.AppSecurityProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    private final AppSecurityProperties props;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return false;

        var rules = props.getPassword();

        if (value.length() < rules.getMinLength()) {
            build(ctx, "Password must be at least " + rules.getMinLength() + " characters");
            return false;
        }
        if (rules.isRequireLetter() && !value.chars().anyMatch(Character::isLetter)) {
            build(ctx, "Password must contain at least one letter");
            return false;
        }
        if (rules.isRequireDigit() && !value.chars().anyMatch(Character::isDigit)) {
            build(ctx, "Password must contain at least one digit");
            return false;
        }
        return true;
    }

    private void build(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
