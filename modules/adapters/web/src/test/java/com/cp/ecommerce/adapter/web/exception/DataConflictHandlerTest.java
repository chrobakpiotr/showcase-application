package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.tracing.Tracer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API-DATA-CONFLICT-001 through Spring MVC: routing, status, problem body and no database text in the response. */
class DataConflictHandlerTest {

    private static final String TEST_PATH = "/data-conflict-test";
    private static final String REJECTED_VALUE = "SKU-REJECTED-1";
    private static final String STATEMENT_FAILED = "could not execute statement";
    private static final String UNIQUE_VIOLATION = "23505";

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracerProvider);

    @Test
    void shouldReturnDataConflictProblemForClientDataUniqueKeyViolation() throws Exception {
        perform(new DataIntegrityViolationException(STATEMENT_FAILED, violation(UNIQUE_VIOLATION, "uq_product_sku")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem-type:data-conflict"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.DATA_CONFLICT_MESSAGE))
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(content().string(not(containsString(REJECTED_VALUE))))
                .andExpect(content().string(not(containsString("uq_product_sku"))));
    }

    @Test
    void shouldReturnDataConflictWhenTheViolationIsWrappedAtCommit() throws Exception {
        perform(new TransactionSystemException("commit failed", violation(UNIQUE_VIOLATION, "UK_IDEMPOTENCY_KEY")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem-type:data-conflict"));
    }

    @Test
    void shouldReturnInternalErrorForServerGeneratedUniqueKeyViolation() throws Exception {
        final RuntimeException cause = violation(UNIQUE_VIOLATION, "uk_order_order_number");
        assertInternalError(perform(new DataIntegrityViolationException(STATEMENT_FAILED, cause)));
    }

    @Test
    void shouldReturnInternalErrorForUnnamedUniqueKeyViolation() throws Exception {
        final SQLException cause = new SQLException("Key (sku)=(" + REJECTED_VALUE + ") already exists.", UNIQUE_VIOLATION);
        assertInternalError(perform(new DataIntegrityViolationException(STATEMENT_FAILED, cause)));
    }

    @Test
    void shouldReturnInternalErrorForOtherIntegrityViolations() throws Exception {
        final RuntimeException cause = violation("23502", "uq_product_sku");
        assertInternalError(perform(new DataIntegrityViolationException(STATEMENT_FAILED, cause)));
    }

    private ResultActions perform(final RuntimeException exception) throws Exception {
        return MockMvcBuilders.standaloneSetup(new FailingController(exception))
                .setControllerAdvice(handler)
                .build()
                .perform(get(TEST_PATH));
    }

    private static void assertInternalError(final ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:problem-type:internal-error"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.RUNTIME_EXCEPTION_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString(REJECTED_VALUE))));
    }

    private static RuntimeException violation(final String sqlState, final String constraintName) {
        final SQLException cause = new SQLException("Key (sku)=(" + REJECTED_VALUE + ") already exists.", sqlState);
        return new ConstraintViolationException(STATEMENT_FAILED, cause, constraintName);
    }

    @RestController
    private static final class FailingController {

        private final RuntimeException exception;

        private FailingController(final RuntimeException exception) {
            this.exception = exception;
        }

        @GetMapping(TEST_PATH)
        String fail() {
            throw exception;
        }
    }
}
