package com.cp.ecommerce.foundation.validation;

import java.util.Collections;
import java.util.Set;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;

@DomainObject
@Getter
public class ValidDomainObject<T> {

    private Set<ConstraintViolation<T>> violations = Collections.emptySet();

    @SuppressWarnings("unchecked")
    protected T validate() {

        final Set<ConstraintViolation<T>> violations = DefaultDomainObjectValidator.get().validate((T) this);
        if (!violations.isEmpty()) {
            this.violations = violations;
        }
        return (T) this;
    }

    public void assertValidationsEmpty() {

        this.getViolations().stream().findAny().ifPresent(violation -> {
            throw new DomainObjectValidationException(
                    ValidationConstants.VALIDATION_FAILED + violation.getMessage(),
                    this.getViolations());
        });
    }
}
