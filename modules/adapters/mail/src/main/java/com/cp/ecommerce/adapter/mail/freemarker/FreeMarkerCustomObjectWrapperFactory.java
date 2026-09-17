package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.Collection;
import java.util.Objects;

import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableStringConverter;

import org.springframework.stereotype.Component;

import freemarker.template.ObjectWrapper;
import lombok.RequiredArgsConstructor;

/**
 * Factory used for wrapping objects that take part in FreeMarker templates' processing.
 */
@RequiredArgsConstructor
@Component
public class FreeMarkerCustomObjectWrapperFactory {

    private final transient Collection<CustomObjectWrapperFactory> customObjectWrapperFactories;

    private final transient TranslatableStringConverter translatableStringConverter;

    public ObjectWrapper createWrapper(final Object model) {

        final FreeMarkerCustomObjectWrapper freemarkerCustomObjectWrapper = new FreeMarkerCustomObjectWrapper(
                translatableStringConverter);

        return customObjectWrapperFactories.stream()
                .map(custom -> custom.createWrapperBasedOnModelType(model, freemarkerCustomObjectWrapper))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(freemarkerCustomObjectWrapper);
    }

}
