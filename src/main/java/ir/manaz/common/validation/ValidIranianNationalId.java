package ir.manaz.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a 10-digit Iranian national ID (کد ملی): length + not all-identical digits.
 * Null values are allowed — combine with {@code @NotBlank} if the field is required.
 */
@Documented
@Constraint(validatedBy = IranianNationalIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIranianNationalId {

    String message() default "کد ملی معتبر نیست";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
