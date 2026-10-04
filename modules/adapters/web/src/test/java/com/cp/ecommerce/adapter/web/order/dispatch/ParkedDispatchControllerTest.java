package com.cp.ecommerce.adapter.web.order.dispatch;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.dispatch.ParkedDispatch;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchPage;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.GetParkedDispatchesInPort;

import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParkedDispatchControllerTest {
    private static final String PATH = "/api/order-placement/dispatches/parked";

    @Test
    void shouldSerializeOnlySafeFieldsAndNoDueTime() throws Exception {
        final var port = mock(GetParkedDispatchesInPort.class);
        when(port.getParkedDispatches(new ParkedDispatchQuery(0, 20))).thenReturn(new ParkedDispatchPage(
                List.of(new ParkedDispatch("id", "ORDER-1", "CONFIRMATION_EMAIL", 8,
                        Instant.parse("2026-10-04T12:00:00Z"), ParkedDispatch.ReasonCode.OTHER)),
                0, 20, 1, 1, 120L));
        MockMvcBuilders.standaloneSetup(new ParkedDispatchController(port)).build().perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].dispatchId").value("id"))
                .andExpect(jsonPath("$.content[0].orderNumber").value("ORDER-1"))
                .andExpect(jsonPath("$.content[0].dispatchType").value("CONFIRMATION_EMAIL"))
                .andExpect(jsonPath("$.content[0].status").value("PARKED"))
                .andExpect(jsonPath("$.content[0].attempts").value(8))
                .andExpect(jsonPath("$.content[0].nextAttemptAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].reasonCode").value("OTHER"))
                .andExpect(jsonPath("$.content[0].lastError").doesNotExist())
                .andExpect(jsonPath("$.content[0].claimId").doesNotExist())
                .andExpect(jsonPath("$.content[0].claimUntil").doesNotExist())
                .andExpect(jsonPath("$.oldestAgeSeconds").value(120));
    }

    @Test
    void shouldRejectInvalidBoundsBeforeCallingQueryPort() throws Exception {
        final var port = mock(GetParkedDispatchesInPort.class);
        final var mvc = MockMvcBuilders.standaloneSetup(new ParkedDispatchController(port)).build();
        for (final String query : new String[] { "?page=-1", "?page=1001", "?size=0", "?size=51", "?page=bad" }) {
            mvc.perform(get(PATH + query)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(port);
    }
}
