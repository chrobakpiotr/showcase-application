package com.cp.ecommerce.adapter.mail.freemarker;

import java.io.IOException;
import java.io.StringWriter;
import java.util.function.Consumer;

import freemarker.core.Environment;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.NoArgsConstructor;

/**
 * Helper class used during FreeMarker's template processing.
 */
@NoArgsConstructor
public class FreeMarkerUtils {

    public static final FreeMarkerUtils INSTANCE = new FreeMarkerUtils();

    /**
     * Processes the specified FreeMarker template with the given model/wrapper and writes the result to the given writer.
     *
     * @param model the model object, typically a Map that contains model names as keys and model objects as values.
     * @param wrapper custom wrapper.
     * @return the result as String.
     * @throws IOException if the template wasn't found or couldn't be read.
     * @throws TemplateException if rendering failed.
     * @see org.springframework.mail.MailPreparationException
     */
    public String processTemplateIntoString(final Template template, final Object model, final ObjectWrapper wrapper)
            throws IOException, TemplateException {

        return processTemplateIntoString(template, model, wrapper, environment -> {
        });
    }

    /**
     * Processes the specified FreeMarker template with the given model/wrapper and writes the result to the given writer.
     *
     * @param model the model object, typically a Map that contains model names as keys and model objects as values.
     * @param wrapper custom wrapper.
     * @param environmentConfigurer consumer which can add configurations to freemarker which are only valid during the
     *            processing of the freemarker template.
     * @return the result as String.
     * @throws IOException if the template wasn't found or couldn't be read.
     * @throws TemplateException if rendering failed.
     * @see org.springframework.mail.MailPreparationException
     */
    public String processTemplateIntoString(
            final Template template,
            final Object model,
            final ObjectWrapper wrapper,
            final Consumer<Environment> environmentConfigurer) throws IOException, TemplateException {

        final StringWriter result = new StringWriter();
        final Environment environment = template.createProcessingEnvironment(model, result, wrapper);
        environmentConfigurer.accept(environment);
        environment.process();
        return result.toString();
    }

}
