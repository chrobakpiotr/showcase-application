# Historical Dependency Review Action candidates

This correction index is deliberately outside the historical evidence
manifests. The README files and `SHA256SUMS` inside each archived package remain
byte-for-byte unchanged.

| Archived package | Status | Correction |
| --- | --- | --- |
| [`070b3b5` package](../evidence/dependency-review-action-v5.0.0-070b3b5/README.md) | **SUPERSEDED — DO NOT APPLY** | Its workflow patch pins the original candidate and does not contain the later snapshot-warning fix. See the [current upload-only prototype](upload-only-prototype/README.md). |
| [`successor-draff241` package](successor-draff241/README.md) | **SUPERSEDED — DO NOT APPLY** | It predates the complete recursive distribution inventory and the current SDK integrity and workflow fixes. See the [current upload-only prototype](upload-only-prototype/README.md). |

The package-local README files retain their original hashes. This index is a
separate correction so that the historical archives are not silently rewritten.
