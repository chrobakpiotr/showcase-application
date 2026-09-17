package com.cp.ecommerce.adapter.web.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Log order adapter test.
 */
@ExtendWith(MockitoExtension.class)
class LogOrderAdapterTest {

    @Mock
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
    void shouldThrowErrorWhileWriteOrderInJson() throws JacksonException {

        final ObjectWriter objectWriter = Mockito.mock(ObjectWriter.class);
        given(objectMapper.writerWithDefaultPrettyPrinter()).willReturn(objectWriter);
        given(objectWriter.writeValueAsString(any())).willThrow(new JacksonException("test") {
        });

        logOrderAdapter.log(null);

        verify(objectMapper, Mockito.atLeastOnce()).writerWithDefaultPrettyPrinter();
    }

}
