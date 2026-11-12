package com.cp.ecommerce.adapter.mail.freemarker;

import java.io.IOException;
import java.util.Map;

import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.mail.freemarker.FreeMarkerTemplateProcessor.ERROR_MESSAGE;

/**
 * Test cases for {@link FreeMarkerTemplateProcessor} class.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class FreeMarkerTemplateProcessorTest {

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
    private transient Object model;

    @Test
    public void shouldWrapTemplateExceptionInTechnicalProblemException() throws IOException, TemplateException {

        final String templateName = "default-text";
        when(configuration.getTemplate(TEMPLATE_PATH + "/" + templateName)).thenReturn(template);
        when(template.createProcessingEnvironment(eq(model), any(), any())).thenThrow(new TemplateException(null));

        assertThatThrownBy(() -> createTemplateProcessor().processTemplate(templateName, TEMPLATE_PATH, model))
                .isInstanceOf(TechnicalProblemException.class)
                .hasNoCause()
                .hasMessageContaining(String.format(ERROR_MESSAGE, model.getClass().getSimpleName(), templateName))
                .hasMessageEndingWith(" at line null, column null (causes=TemplateException)");
        verify(configuration).setLogTemplateExceptions(false);
        verify(configuration).setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    @Test
    public void shouldWrapMessageExceptionOnTemplateNotFoundException() throws IOException {

        final String templateName = "default-text";
        when(configuration.getTemplate(TEMPLATE_PATH + "/" + templateName)).thenThrow(new IOException("not found"));
        final String expected = String.format(ERROR_MESSAGE, model.getClass().getSimpleName(), templateName);

        assertThatThrownBy(() -> createTemplateProcessor().processTemplate(templateName, TEMPLATE_PATH, model))
                .isInstanceOf(TechnicalProblemException.class)
                .hasNoCause()
                .hasMessage(expected + " (causes=IOException)");
    }

    @Test
    public void shouldReturnEmptyTemplateIfOneOrManyProcessedParametersAreNull() {

        assertThat(createTemplateProcessor().processTemplate(null, null, model)).isNull();
    }

    @Test
    public void shouldNotPutModelDataIntoTheFailureMessage() {

        final Map<String, String> customerModel = Map.of("email", "buyer@example.com");
        final FreeMarkerTemplateProcessor processor = new FreeMarkerTemplateProcessor(configuration, objectWrapperFactory);

        assertThatThrownBy(() -> processor.processTemplate("default-text", TEMPLATE_PATH, customerModel))
                .isInstanceOf(TechnicalProblemException.class)
                .hasMessageNotContaining("buyer@example.com");
    }

    private FreeMarkerTemplateProcessor createTemplateProcessor() {

        when(objectWrapperFactory.createWrapper(model)).thenReturn(objectWrapper);
        return new FreeMarkerTemplateProcessor(configuration, objectWrapperFactory);
    }

}
