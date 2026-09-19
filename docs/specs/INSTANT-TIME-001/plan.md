# Plan - INSTANT-TIME-001

1. Normalize the interrupted migration and convert every Java timestamp model to Instant.
2. Isolate unavoidable third-party java.util.Date conversion in one interop bridge.
3. Configure persistence time handling for UTC.
4. Register Gson Instant serialization.
5. Add regression guard and run full repository gates.
