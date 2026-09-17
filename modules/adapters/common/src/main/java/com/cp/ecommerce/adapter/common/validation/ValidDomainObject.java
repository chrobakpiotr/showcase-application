package com.cp.ecommerce.adapter.common.validation;

import java.util.Collections;
import java.util.Set;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.ConstraintViolation;
import lombok.Getter;

/**
 * Base for all self validating domain objects.
 */
@DomainObject
@Getter
public class ValidDomainObject<T> {

    @JsonIgnore
    private Set<ConstraintViolation<T>> violations = Collections.emptySet();

    /**
     * Evaluates all bean validations on the attributes of this instance.
     */
    @SuppressWarnings("unchecked")
    protected T validate() {

        final Set<ConstraintViolation<T>> violations = DefaultDomainObjectValidator.get().validate((T) this);
        if (!violations.isEmpty()) {

            this.violations = violations;
        }
        return (T) this;
    }

    /**
     * Checks for validation and throw exception if found.
     */
    public void assertValidationsEmpty() {

        this.getViolations().stream().findAny().ifPresent(violation -> {

            throw new DomainObjectValidationException(
                    ValidationConstants.VALIDATION_FAILED + violation.getMessage(),
                    this.getViolations());
        });
    }

}
