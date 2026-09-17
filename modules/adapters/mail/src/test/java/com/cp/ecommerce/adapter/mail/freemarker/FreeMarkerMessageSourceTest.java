package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.MessageSource;

import freemarker.template.TemplateModelException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Test cases for {@link FreeMarkerMessageSource} class.
 */
@ExtendWith(MockitoExtension.class)
class FreeMarkerMessageSourceTest {

    private static final String MESSAGE = "message";

    private static final String TEST_MESSAGE = "test.message";

    @Mock
    private transient MessageSource messageSource;

    @Test
    public void shouldResolveMessageWithEmptyStringWhenArgumentsAreEmpty() throws Exception {

        assertThat(processTestMessage(new ArrayList<>())).isEqualTo(StringUtils.EMPTY);
    }

    @Test
    public void shouldResolveMessageWithEmptyStringWhenArgumentsAreNull() throws Exception {

        assertThat(processTestMessage(null)).isEqualTo(StringUtils.EMPTY);
    }

    @Test
    public void shouldResolveMessageWithMessageCode() throws Exception {

        given(messageSource.getMessage(TEST_MESSAGE, new Object[0], Locale.getDefault())).willReturn(MESSAGE);
        assertThat(processTestMessage(Collections.singletonList(TEST_MESSAGE))).isEqualTo(MESSAGE);
    }

    @Test
    public void shouldResolveMessageWithMessageCodeAndArguments() throws Exception {

        given(messageSource.getMessage(TEST_MESSAGE, new Object[] { "arg1", "arg2" }, Locale.getDefault())).willReturn(MESSAGE);
        assertThat(processTestMessage(Arrays.asList(TEST_MESSAGE, "arg1", "arg2"))).isEqualTo(MESSAGE);
    }

    private String processTestMessage(final List<?> arguments) throws TemplateModelException {

        return String.valueOf(new FreeMarkerMessageSource(messageSource).templateMethodModel().exec(arguments));
    }

}
