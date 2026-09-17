package com.cp.ecommerce.adapter.mail.freemarker;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.StrongCacheStorage;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

/**
 * FreeMarker template processor, which is the entry point for the whole PDF generation process.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class FreeMarkerTemplateProcessor {

    private static final String TEMPLATES_BASE_PATH = "templates";

    private static final String FILE_SEPARATOR = "/";

    public static final String ERROR_MESSAGE = "Failed to transform model '%s' using template '%s'";

    private final transient Configuration configuration;

    private final transient FreeMarkerCustomObjectWrapperFactory objectWrapperFactory;

    private final transient FreeMarkerUtils freeMarkerUtils = FreeMarkerUtils.INSTANCE;

    private transient ClassTemplateLoader classTemplateLoader;

    public String processTemplate(final String templateName, final String templatePath, final Object model) {

        if (nonNullParameters(templateName, model)) {

            try {
                setupFreeMarkerConfiguration();
                final Template template = configuration.getTemplate(templatePath + FILE_SEPARATOR + templateName);
                return freeMarkerUtils.processTemplateIntoString(
                        template,
                        model,
                        objectWrapperFactory.createWrapper(model),
                        new EnvironmentConfigurer().getConfigurer());
            } catch (final TemplateException | IOException | RuntimeException ex) { // SUPPRESS CHECKSTYLE IllegalCatch
                throw new TechnicalProblemException(format(ERROR_MESSAGE, model, templateName), ex);
            }
        }
        return null;
    }

    private void setupFreeMarkerConfiguration() {

        if (Optional.ofNullable(classTemplateLoader).isEmpty()) {
            classTemplateLoader = new ClassTemplateLoader(Thread.currentThread().getContextClassLoader(), TEMPLATES_BASE_PATH);
            configuration.setTemplateLoader(classTemplateLoader);
            configuration.setCacheStorage(new StrongCacheStorage());
        }
    }

    private boolean nonNullParameters(final Object... args) {

        final AtomicBoolean nonNull = new AtomicBoolean(true);
        Optional.ofNullable(args).ifPresent(parameters -> Arrays.stream(parameters).forEach(param -> {

            try {
                Objects.requireNonNull(param, "Parameter cannot be null");
            } catch (final RuntimeException e) { // SUPPRESS CHECKSTYLE IllegalCatch
                nonNull.set(false);
                log.error("Error during processing PDF template", e);
            }
        }));
        return nonNull.get();
    }

    static class EnvironmentConfigurer {

        Consumer<Environment> getConfigurer() {

            return environment -> environment.setLocale(LocaleContextHolder.getLocale());
        }

    }

}
