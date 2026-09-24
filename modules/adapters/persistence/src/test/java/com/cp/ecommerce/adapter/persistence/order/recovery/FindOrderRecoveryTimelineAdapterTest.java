package com.cp.ecommerce.adapter.persistence.order.recovery;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FindOrderRecoveryTimelineAdapterTest {

    private static final String ORDER_NUMBER = "ORD-1001";

    private static final String ORDER_NUMBER_PARAM = "orderNumber";

    private static final String BASE_INSTANT = "2026-09-24T12:00:00Z";

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private FindOrderRecoveryTimelineAdapter adapter;

    @Test
    void shouldUseOneBoundedQueryAndMapOnlySafeProjectionFields() {

        given(entityManager.createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL)).willReturn(query);
        given(query.setParameter(ORDER_NUMBER_PARAM, ORDER_NUMBER)).willReturn(query);
        given(query.setFirstResult(50)).willReturn(query);
        given(query.setMaxResults(25)).willReturn(query);
        given(query.getResultList()).willReturn(
                List.<Object[]> of(
                        new Object[] {
                                "PLACEMENT_DISPATCH",
                                "CONFIRMATION_EMAIL",
                                "UNKNOWN",
                                Timestamp.from(Instant.parse(BASE_INSTANT)),
                                "ORDER-CONFIRMATION:ORD-1001",
                                "FAILED" }));

        final var result = adapter.find(ORDER_NUMBER, 2, 25);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().state()).isEqualTo(OrderRecoveryTimelineState.UNKNOWN);
        assertThat(result.getFirst().referenceId()).isEqualTo("ORDER-CONFIRMATION:ORD-1001");
        assertThat(result.getFirst().summary()).isEqualTo("PLACEMENT_DISPATCH CONFIRMATION_EMAIL state FAILED");

        verify(entityManager, times(1)).createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL);
        verify(query).setParameter(ORDER_NUMBER_PARAM, ORDER_NUMBER);
        verify(query).setFirstResult(50);
        verify(query).setMaxResults(25);
        verify(query, times(1)).getResultList();
    }

    @Test
    void queryMustNotSelectSensitiveRecoveryFields() {

        final String sql = FindOrderRecoveryTimelineAdapter.TIMELINE_SQL.toUpperCase(Locale.ROOT);

        assertThat(sql).doesNotContain("CLAIM_ID");
        assertThat(sql).doesNotContain("CLAIM_UNTIL");
        assertThat(sql).doesNotContain("LAST_ERROR");
        assertThat(sql).doesNotContain("RECIPIENT_EMAIL");
        assertThat(sql).doesNotContain("SUBJECT");
        assertThat(sql).doesNotContain("BODY");
        assertThat(sql).doesNotContain("TRACKING_NUMBER");
    }

    @Test
    void shouldMapSupportedTimestampRepresentationsAndKeepOneQuery() {

        given(entityManager.createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL)).willReturn(query);
        given(query.setParameter(ORDER_NUMBER_PARAM, ORDER_NUMBER)).willReturn(query);
        given(query.setFirstResult(0)).willReturn(query);
        given(query.setMaxResults(10)).willReturn(query);
        given(query.getResultList()).willReturn(
                List.<Object[]> of(
                        row(Instant.parse(BASE_INSTANT), "instant"),
                        row(OffsetDateTime.parse("2026-09-24T12:01:00Z"), "offset"),
                        row(LocalDateTime.of(2026, 9, 24, 12, 2), "local")));

        final var result = adapter.find(ORDER_NUMBER, 0, 10);

        assertThat(result).extracting(entry -> entry.occurredAt())
                .containsExactly(
                        Instant.parse(BASE_INSTANT),
                        Instant.parse("2026-09-24T12:01:00Z"),
                        LocalDateTime.of(2026, 9, 24, 12, 2).toInstant(ZoneOffset.UTC));

        verify(entityManager, times(1)).createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL);
        verify(query, times(1)).getResultList();
    }

    @Test
    void shouldFailClosedForMissingSafeProjectionField() {

        given(entityManager.createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL)).willReturn(query);
        given(query.setParameter(ORDER_NUMBER_PARAM, ORDER_NUMBER)).willReturn(query);
        given(query.setFirstResult(0)).willReturn(query);
        given(query.setMaxResults(10)).willReturn(query);
        given(query.getResultList()).willReturn(
                List.<Object[]> of(
                        new Object[] {
                                null,
                                "RABBITMQ",
                                "COMPLETED",
                                Timestamp.from(Instant.parse(BASE_INSTANT)),
                                "op-1",
                                "RECEIVED" }));

        assertThatThrownBy(() -> adapter.find(ORDER_NUMBER, 0, 10)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing safe field");
    }

    @Test
    void shouldFailClosedForUnsupportedTimestampType() {

        given(entityManager.createNativeQuery(FindOrderRecoveryTimelineAdapter.TIMELINE_SQL)).willReturn(query);
        given(query.setParameter(ORDER_NUMBER_PARAM, ORDER_NUMBER)).willReturn(query);
        given(query.setFirstResult(0)).willReturn(query);
        given(query.setMaxResults(10)).willReturn(query);
        given(query.getResultList()).willReturn(List.<Object[]> of(row("not-a-timestamp", "bad-time")));

        assertThatThrownBy(() -> adapter.find(ORDER_NUMBER, 0, 10)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported recovery timeline timestamp type");
    }

    @Test
    void notificationScopeMustUseLiteralFormalPrefixInsteadOfSqlWildcardMatch() {

        final String sql = FindOrderRecoveryTimelineAdapter.TIMELINE_SQL.toUpperCase(Locale.ROOT);

        assertThat(sql).contains("LEFT(EVENT_KEY");
        assertThat(sql).contains("LENGTH(CONCAT('ORDER:', :ORDERNUMBER, ':'))");
        assertThat(sql).doesNotContain("EVENT_KEY LIKE");
    }

    private static Object[] row(final Object timestamp, final String referenceId) {

        return new Object[] { "FULFILLMENT", "RABBITMQ", "COMPLETED", timestamp, referenceId, "RECEIVED" };
    }

}
