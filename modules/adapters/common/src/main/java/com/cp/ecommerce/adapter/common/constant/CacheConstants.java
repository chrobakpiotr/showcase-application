package com.cp.ecommerce.adapter.common.constant;

import java.time.Duration;

import lombok.experimental.UtilityClass;

/**
 * Class with cache constants.
 */
@UtilityClass
public class CacheConstants {

    public final String ORDER_CACHE_NAME = "orderCache";

    public final Duration ONE_HOUR_DURATION = Duration.ofHours(1);

    public final Long ONE_HUNDRED_ENTRIES = 100L;

}
