package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import freemarker.template.TemplateMethodModelEx;
import lombok.RequiredArgsConstructor;

/**
 * A message source which uses the locale of the freemarker context - but this only works if it is called during template
 * execution.
 */
@RequiredArgsConstructor
@Component
public class FreeMarkerMessageSource {

    private final transient MessageSource messageSource;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public TemplateMethodModelEx templateMethodModel() {

        return arguments -> {
            if (arguments == null || arguments.isEmpty()) {
                return StringUtils.EMPTY;
            }
            final String messageKey = String.valueOf(arguments.get(0));
            final List args = (List) arguments.stream().skip(1).collect(Collectors.toList());
            return getMessageForLocale(messageKey, args.toArray());
        };
    }

    private String getMessageForLocale(final String key, final Object... args) {

        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

}
