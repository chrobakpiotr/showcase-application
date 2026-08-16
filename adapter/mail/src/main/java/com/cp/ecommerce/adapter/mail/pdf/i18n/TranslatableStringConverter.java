package com.cp.ecommerce.adapter.mail.pdf.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Converter class for translatable string used during FreeMarker template processing.
 */
@RequiredArgsConstructor
@Component
public class TranslatableStringConverter implements Converter<TranslatableString, String> {

    private final transient MessageSource messageSource;

    @Override
    public String convert(final TranslatableString string) {

        return convert(string, LocaleContextHolder.getLocale());
    }

    public String convert(final TranslatableString string, final Locale locale) {

        return messageSource.getMessage(string.key(), null, string.defaultMessage(), locale);
    }

}
