package com.cp.ecommerce.domain.order.port.outgoing;

import java.util.List;

/** Finds durable cancellation intents that need autonomous recovery. */
public interface FindOrderCancellationCandidatesOutPort {

    List<String> findCancellationCandidates(int limit);
}
