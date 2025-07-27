package com.bloom.app.validation;

import com.bloom.app.validation.validator.UniqueItemSkuValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = UniqueItemSkuValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueItemSku {
    String message() default "Item SKU already exists.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
