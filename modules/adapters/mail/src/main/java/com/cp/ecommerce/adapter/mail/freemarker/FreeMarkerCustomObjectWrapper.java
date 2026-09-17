package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.Optional;

import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableString;
import com.cp.ecommerce.adapter.mail.pdf.i18n.TranslatableStringConverter;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * Object wrapper for FreeMarker templates' processing.
 */
public class FreeMarkerCustomObjectWrapper extends DefaultObjectWrapper {

    private final transient TranslatableStringConverter translatableStringConverter;

    public FreeMarkerCustomObjectWrapper(final TranslatableStringConverter translatableStringConverter) {

        // incompatibleImprovements pinned to the library version in use (rather than the deprecated no-arg super
        // constructor's legacy 2.3.0 default) so that Java record accessors (e.g. AddressFtl.street()) are exposed to
        // templates as bare properties (${address.street}), not only as explicit method calls (${address.street()}).
        super(Configuration.VERSION_2_3_34);
        this.translatableStringConverter = translatableStringConverter;
    }

    @Override
    public TemplateModel handleUnknownType(final Object obj) throws TemplateModelException {

        return switch (obj) {
        case Optional<?> optional -> wrap(optional.orElse(null));
        case TranslatableString translatable -> wrap(translatableStringConverter.convert(translatable));
        default -> super.handleUnknownType(obj);
        };
    }

}
