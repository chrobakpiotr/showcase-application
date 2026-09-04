package com.cp.ecommerce.adapter.ai.analytics;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.stream.Collectors;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools the ops-analytics assistant's model can call to ground its answers in the platform's actual, current data (see ADR
 * 0021) instead of guessing. Not exposed as a Spring bean beyond this module - the model decides for itself, per question,
 * whether calling either tool is relevant.
 *
 * <p>
 * Deliberately read-only, mirroring {@code OrderLookupTool}'s design in the support-assistant feature: it wraps only the two
 * existing query use cases, never anything that could mutate order or saga state on an operator's behalf.
 * </p>
 */
@Component
class OrderAnalyticsTool {

    private final CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort;

    private final GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort;

    OrderAnalyticsTool(
            final CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort,
            final GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort) {

        this.countOrderAnalyticsProjectionsInPort = countOrderAnalyticsProjectionsInPort;
        this.getRemarksClassificationSummaryInPort = getRemarksClassificationSummaryInPort;
    }

    @Tool(
            name = "countOrdersPlacedBetween",
            description = "Counts how many orders were placed within an inclusive date range, both boundaries included in "
                    + "full (UTC). Use this whenever an operator asks how many orders were placed in some period. Dates "
                    + "must be given as ISO-8601 calendar dates, e.g. 2024-01-31.")
    String countOrdersPlacedBetween(
            @ToolParam(description = "Start date (inclusive), ISO-8601 yyyy-MM-dd.") final String fromDate,
            @ToolParam(description = "End date (inclusive), ISO-8601 yyyy-MM-dd.") final String toDate) {

        final LocalDate from;
        final LocalDate to;
        try {
            from = LocalDate.parse(fromDate);
            to = LocalDate.parse(toDate);
        } catch (DateTimeParseException exception) {
            return "Could not parse the given dates - please ask for both dates again as ISO-8601 yyyy-MM-dd, "
                    + "e.g. 2024-01-31.";
        }

        final Date fromInclusive = Date.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());
        // "to" is inclusive of the whole day: the last millisecond before the next day starts.
        final Date toInclusive = Date.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1));
        final long count = countOrderAnalyticsProjectionsInPort.countPlacedBetween(fromInclusive, toInclusive);
        return "%d order(s) were placed between %s and %s (inclusive, UTC).".formatted(count, from, to);
    }

    @Tool(
            name = "remarksClassificationBreakdown",
            description = "Returns how many orders' free-text remarks have been classified into each AI remarks-triage "
                    + "category (STANDARD, URGENT, COMPLAINT, SUSPICIOUS) since this application instance started. Use "
                    + "this whenever an operator asks about remarks classification, triage, or which orders look urgent, "
                    + "suspicious or complaint-related.")
    String remarksClassificationBreakdown() {

        final RemarksClassificationSummary summary = getRemarksClassificationSummaryInPort.getSummary();
        return summary.countsByCategory()
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

}
