package com.cp.ecommerce.adapter.mail.freemarker;

import java.io.IOException;
import java.util.Locale;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.mail.freemarker.FreeMarkerTemplateProcessor.ERROR_MESSAGE;

/**
 * Test cases for {@link FreeMarkerTemplateProcessor} class.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class FreeMarkerTemplateProcessorTest {

    private static final Locale LOCALE = Locale.UK;

    private static final String TEMPLATE_PATH = "default-path";

    @Mock
    private transient Template template;

    @Mock
    private transient Configuration configuration;

    @Mock
    private transient FreeMarkerCustomObjectWrapperFactory objectWrapperFactory;

    @Mock
    private transient FreeMarkerCustomObjectWrapper objectWrapper;

    @Mock
    private transient FreeMarkerUtils freeMarkerUtils;

    @Mock
    private transient Object model;

    @Test
    public void shouldWrapTemplateExceptionInTechnicalProblemException() throws IOException, TemplateException {

        final String templateName = "default-text";
        when(configuration.getTemplate(templateName, LOCALE)).thenReturn(template);
        doThrow(new TemplateException(null)).when(freeMarkerUtils)
                .processTemplateIntoString(eq(template), eq(model), any(), any());

        assertThatThrownBy(() -> createTemplateProcessor().processTemplate(templateName, TEMPLATE_PATH, model))
                .isInstanceOf(TechnicalProblemException.class)
                .hasMessageContaining(String.format(ERROR_MESSAGE, model, templateName));
    }

    @Test
    public void shouldWrapMessageExceptionOnTemplateNotFoundException() throws IOException, TemplateException {

        final String templateName = "default-text";
        when(configuration.getTemplate(templateName, LOCALE)).thenReturn(template);
        doThrow(IOException.class).when(freeMarkerUtils).processTemplateIntoString(eq(template), eq(model), any(), any());

        assertThatThrownBy(() -> createTemplateProcessor().processTemplate(templateName, TEMPLATE_PATH, model))
                .isInstanceOf(TechnicalProblemException.class)
                .hasMessageContaining(String.format(ERROR_MESSAGE, model, templateName));
    }

    @Test
    public void shouldReturnEmptyTemplateIfOneOrManyProcessedParametersAreNull() {

        assertThat(createTemplateProcessor().processTemplate(null, null, model)).isNull();
    }

    private FreeMarkerTemplateProcessor createTemplateProcessor() {

        when(objectWrapperFactory.createWrapper(model)).thenReturn(objectWrapper);
        return new FreeMarkerTemplateProcessor(configuration, objectWrapperFactory);
    }

}
