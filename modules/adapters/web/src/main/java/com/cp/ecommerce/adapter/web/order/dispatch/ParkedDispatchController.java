package com.cp.ecommerce.adapter.web.order.dispatch;

import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.GetParkedDispatchesInPort;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ParkedDispatchController {

    private final GetParkedDispatchesInPort port;

    @GetMapping("/api/order-placement/dispatches/parked")
    public ParkedDispatchPageResource listParked(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "20") final int size) {
        final ParkedDispatchQuery query;
        try {
            query = new ParkedDispatchQuery(page, size);
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        final var result = port.getParkedDispatches(query);
        return new ParkedDispatchPageResource(
                result.content()
                        .stream()
                        .map(
                                row -> new ParkedDispatchResource(
                                        row.dispatchId(),
                                        row.orderNumber(),
                                        row.dispatchType(),
                                        "PARKED",
                                        row.attempts(),
                                        row.createdAt(),
                                        null,
                                        row.reasonCode().name()))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.oldestAgeSeconds());
    }
}
