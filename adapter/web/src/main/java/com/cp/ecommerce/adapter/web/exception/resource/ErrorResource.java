package com.cp.ecommerce.adapter.web.exception.resource;

import lombok.Builder;

@Builder
public record ErrorResource(String id, String message) {

}
