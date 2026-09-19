#!/usr/bin/env python3
from pathlib import Path
import sys

allowed = {
    Path("modules/adapters/common/src/main/java/com/cp/ecommerce/adapter/common/time/LegacyDateInterop.java")
}

ignored_parts = {
    "build",
    ".gradle",
    "node_modules",
}

violations = []

for root in (Path("apps"), Path("modules")):
    if not root.exists():
        continue

    for path in root.rglob("*.java"):
        if any(part in ignored_parts for part in path.parts):
            continue

        if path in allowed:
            continue

        text = path.read_text()
        if (
            "import java.util.Date;" in text
            or "java.util.Date" in text
            or "java.util.Instant" in text
            or "new Instant(" in text
        ):
            violations.append(str(path))

if violations:
    print("Legacy/broken Java time usage remains outside the explicit interop boundary:")
    for path in violations:
        print(f" - {path}")
    sys.exit(1)

print("PASS: application Java time model is Instant; generated build trees ignored; legacy Date is isolated")
