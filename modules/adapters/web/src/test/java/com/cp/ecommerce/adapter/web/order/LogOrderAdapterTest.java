package com.cp.ecommerce.adapter.web.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Log order adapter test.
 */
@ExtendWith(MockitoExtension.class)
class LogOrderAdapterTest {

    @Spy
    private transient ObjectMapper objectMapper;

    @InjectMocks
    private transient LogOrderAdapter logOrderAdapter;

    @Test
    void shouldWriteOrderInJsonSuccessfully() {

        given(objectMapper.writerWithDefaultPrettyPrinter()).willReturn(Mockito.mock(ObjectWriter.class));

        logOrderAdapter.log(mockOrder());

        verify(objectMapper, Mockito.atLeastOnce()).writerWithDefaultPrettyPrinter();
    }

    @Test
    void shouldThrowErrorWhileWriteOrderInJson() throws JsonProcessingException {

        final ObjectWriter objectWriter = Mockito.mock(ObjectWriter.class);
        given(objectMapper.writerWithDefaultPrettyPrinter()).willReturn(objectWriter);
        given(objectWriter.writeValueAsString(any())).willThrow(new JsonProcessingException("test") {
        });

        logOrderAdapter.log(null);

        verify(objectMapper, Mockito.atLeastOnce()).writerWithDefaultPrettyPrinter();
    }

}
