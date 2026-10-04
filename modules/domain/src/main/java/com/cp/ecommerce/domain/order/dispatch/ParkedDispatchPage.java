package com.cp.ecommerce.domain.order.dispatch;

import java.util.List;

public record ParkedDispatchPage(List<ParkedDispatch> content, int page, int size,
        long totalElements, int totalPages, Long oldestAgeSeconds) {
    public ParkedDispatchPage {
        content = List.copyOf(content);
    }
}
