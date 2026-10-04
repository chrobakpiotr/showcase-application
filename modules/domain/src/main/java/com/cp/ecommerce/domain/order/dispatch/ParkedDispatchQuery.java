package com.cp.ecommerce.domain.order.dispatch;

public record ParkedDispatchQuery(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_PAGE = 1000;

    public ParkedDispatchQuery {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("page must be between 0 and 1000 and size between 1 and 50");
        }
    }
}
