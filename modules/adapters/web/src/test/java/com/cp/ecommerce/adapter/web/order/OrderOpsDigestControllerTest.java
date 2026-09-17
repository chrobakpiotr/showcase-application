package com.cp.ecommerce.adapter.web.order;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.usecase.GetLatestOpsDigestUseCase;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking {@link OrderOpsDigestController}'s behavior and API response.
 */
@WebMvcTest(OrderOpsDigestController.class)
class OrderOpsDigestControllerTest {

    private static final String DIGEST_ENDPOINT = "/api/order/analytics/digest";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient GetLatestOpsDigestUseCase getLatestOpsDigestUseCase;

    @Test
    void shouldReturnLatestDigestWhenOneExists() throws Exception {

        final OpsDigest opsDigest = OpsDigest.builder()
                .generatedDate(new Date())
                .ordersPlacedLastDay(7L)
                .remarksClassificationSummary(new RemarksClassificationSummary(Map.of(RemarksTriageCategory.STANDARD, 7L)))
                .narrative("7 orders placed in the last 24 hours, all routine.")
                .build();
        given(getLatestOpsDigestUseCase.getLatestDigest()).willReturn(Optional.of(opsDigest));

        this.mockMvc.perform(get(DIGEST_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ordersPlacedLastDay").value(7))
                .andExpect(jsonPath("$.narrative").value("7 orders placed in the last 24 hours, all routine."))
                .andExpect(jsonPath("$.remarksClassificationCounts.STANDARD").value(7));
    }

    @Test
    void shouldReturn204WhenNoDigestGeneratedYet() throws Exception {

        given(getLatestOpsDigestUseCase.getLatestDigest()).willReturn(Optional.empty());

        this.mockMvc.perform(get(DIGEST_ENDPOINT)).andDo(print()).andExpect(status().isNoContent());
    }

}
