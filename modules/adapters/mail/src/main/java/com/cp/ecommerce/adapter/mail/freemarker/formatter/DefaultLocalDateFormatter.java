package com.cp.ecommerce.adapter.mail.freemarker.formatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import lombok.experimental.UtilityClass;

/**
 * Utility class used for formatting dates for specific locale.
 */
@UtilityClass
public class DefaultLocalDateFormatter {

    public final String DEFAULT_DATE_PATTERN = "dd.MM.YYYY";

    public final String EMPTY_VALUE = "-";

    public String formatDate(final LocalDate date) {

        return formatDateWithPattern(date, DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN));
    }

    private String formatDateWithPattern(final LocalDate date, final DateTimeFormatter dateTimeFormatter) {

        return Optional.ofNullable(date).map(d -> d.format(dateTimeFormatter)).orElse(EMPTY_VALUE);
    }

}
