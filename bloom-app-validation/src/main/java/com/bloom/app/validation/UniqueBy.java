package com.bloom.app.validation;

import com.bloom.app.validation.validator.UniqueByValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueByValidator.class)
public @interface UniqueBy {
    String message() default "Duplicates found based on property {property}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String property();
}
