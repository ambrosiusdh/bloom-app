package com.bloom.app.validation.validator;

import com.bloom.app.api.validation.UniqueBy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SupportedValidationTarget(ValidationTarget.ANNOTATED_ELEMENT)
public class UniqueByValidator implements ConstraintValidator<UniqueBy, List<?>> {
    private String property;

    @Override
    public void initialize(UniqueBy constraintAnnotation) {
        this.property = constraintAnnotation.property();
    }

    @Override
    public boolean isValid(List<?> value, ConstraintValidatorContext context) {

        if (value == null || value.isEmpty()) {
            return true; // Nothing to validate
        }

        Set<Object> seen = new HashSet<>();

        try {
            for (Object element : value) {
                Field field = element.getClass().getDeclaredField(property);
                field.setAccessible(true);
                Object fieldValue = field.get(element);

                if (!seen.add(fieldValue)) {
                    return false; // Duplicate detected
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate unique property: " + property, e);
        }

        return true;
    }
}
