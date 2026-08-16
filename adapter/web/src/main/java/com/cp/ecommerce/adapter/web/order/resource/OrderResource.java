package com.cp.ecommerce.adapter.web.order.resource;

import java.util.Date;

import lombok.Builder;

/**
 * Resources representing order response.
 */
@Builder
public record OrderResource(String remarks, Date created) {

}
