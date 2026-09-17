package com.cp.ecommerce.adapter.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.experimental.UtilityClass;

/**
 * Default domain objects validator.
 */
@UtilityClass
class DefaultDomainObjectValidator {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    Validator get() {

        return validator;
    }

}
