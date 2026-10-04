package com.cp.ecommerce.adapter.web.exception;

import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.tracing.Tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShipmentConflictHandlerTest {

    private static final String EXCEPTION_MESSAGE = "message";
    private static final String SHIPMENT_CONFLICT_TITLE = "Shipment Conflict";
    private static final String TEST_PATH = "/shipment-conflict-test";
    private static final String PROBLEM_TYPE = "urn:problem-type:business-rule-violation";

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracerProvider);

    @Test
    void shouldHandleShipmentConflictException() {
        assertProblem(handler.shipmentConflictException(new ShipmentConflictException(EXCEPTION_MESSAGE)), EXCEPTION_MESSAGE);
    }

    @Test
    void shouldSerializeOnlyRecognizedShipmentConflictCodesAndPreserveProblemFields() throws Exception {
        for (final ShipmentConflictException.Code code : ShipmentConflictException.Code.values()) {
            final var exception = new ShipmentConflictException(EXCEPTION_MESSAGE, code);
            assertProblem(handler.shipmentConflictException(exception), EXCEPTION_MESSAGE);
            MockMvcBuilders.standaloneSetup(new ShipmentConflictController(exception))
                    .setControllerAdvice(handler)
                    .build()
                    .perform(get(TEST_PATH))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(code.name()))
                    .andExpect(jsonPath("$.type").value(PROBLEM_TYPE))
                    .andExpect(jsonPath("$.title").value(SHIPMENT_CONFLICT_TITLE))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value(EXCEPTION_MESSAGE))
                    .andExpect(jsonPath("$.instance").value(TEST_PATH))
                    .andExpect(jsonPath("$.errorId").isNotEmpty());
        }
    }

    @Test
    void shouldKeepLegacyShipmentConflictResponseWithoutCode() throws Exception {
        final var exception = new ShipmentConflictException(EXCEPTION_MESSAGE);
        assertThat(exception.getCode()).isNull();
        assertThat(handler.shipmentConflictException(exception).getProperties()).doesNotContainKey("code");
        MockMvcBuilders.standaloneSetup(new ShipmentConflictController(exception))
                .setControllerAdvice(handler)
                .build()
                .perform(get(TEST_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.detail").value(EXCEPTION_MESSAGE))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
    }

    private void assertProblem(final ProblemDetail problemDetail, final String detail) {
        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getTitle()).isEqualTo(SHIPMENT_CONFLICT_TITLE);
        assertThat(problemDetail.getDetail()).isEqualTo(detail);
        assertThat(problemDetail.getProperties()).containsKey("errorId");
    }

    @RestController
    private static final class ShipmentConflictController {

        private final ShipmentConflictException exception;

        private ShipmentConflictController(final ShipmentConflictException exception) {
            this.exception = exception;
        }

        @GetMapping(TEST_PATH)
        String conflict() {
            throw exception;
        }
    }
}
