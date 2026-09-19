#!/usr/bin/env python3
from pathlib import Path
import sys

targets = {
    "modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/outbox/OrderPlacementSagaOrchestrator.java":
        ["new Date()", "System.currentTimeMillis()"],
    "modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/idempotency/IdempotencyKeyAdapter.java":
        ["new Date()", "Instant.now()", "System.currentTimeMillis()"],
    "modules/adapters/aws/src/main/java/com/cp/ecommerce/adapter/aws/order/PublishOrderAuditEventAdapter.java":
        ["Instant.now()", "System.currentTimeMillis()"],
    "modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/order/PublishOrderAnalyticsEventAdapter.java":
        ["Instant.now()", "System.currentTimeMillis()"],
}
errors=[]
for name, forbidden in targets.items():
    text=Path(name).read_text()
    if "Clock" not in text:
        errors.append(f"{name}: missing Clock")
    for token in forbidden:
        if token in text:
            errors.append(f"{name}: direct wall clock: {token}")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: critical operational time paths use Clock")
