package com.cp.ecommerce.adapter.persistence.configuration;

import java.time.Duration;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.cp.ecommerce.adapter.common.constant.CacheConstants.ONE_HOUR_DURATION;
import static com.cp.ecommerce.adapter.common.constant.CacheConstants.ONE_HUNDRED_ENTRIES;
import static com.cp.ecommerce.adapter.common.constant.CacheConstants.ORDER_CACHE_NAME;

/**
 * Enum providing cache and its configuration.
 */
@Getter
@AllArgsConstructor
enum CacheProperties {

    ORDER_CACHE(ORDER_CACHE_NAME, String.class, OrderEntity.class, ONE_HOUR_DURATION, ONE_HUNDRED_ENTRIES);

    private final String cacheName;

    private final Class<?> keyType;

    private final Class<?> valueType;

    private final Duration duration;

    private final Long maxEntries;

}
