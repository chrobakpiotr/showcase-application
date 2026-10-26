# Dependency Review Action review correction — 2026-10-26

This addendum is outside the checksum manifests for the historical review reports
and the `successor-fcbc442` evidence package. It corrects their current status
without changing any previously checksummed artifact.

## N1 — review ownership and current verdict

Harness's independent review of action candidate
`fcbc44201b365daec4da85a4cf73adde8b82609c` is recorded on the
`dependency-review-action` branch at commit `c96f5e6` (the short SHA supplied in
the Harness handover). Its verdict is **PASS WITH AMENDMENTS**. The review closes
F5 and leaves N1, N2, N3, and D1 as documentation amendments. The candidate is
not yet ready for an owner publication decision.

This is distinct from `INDEPENDENT-REVIEW.md`: that earlier Showcase review agent
reviewed `4665869fdfe9557a437e61fdc9667b82e5bb414d` only. The earlier
`successor-fcbc442/README.md` sentence saying the successor had not received a
Harness review accurately described its state when written, but is superseded by
the later Harness review above. Both historical files remain unchanged and their
original SHA-256 entries remain valid.

## N2 — current digest mapping

The old whole-package manifest values in the checksummed
`successor-fcbc442/AUDIT-RESOLUTION.md` (`0ed106…` pristine and `0582ee…`
patched) are stale. For the exact `@actions/artifact@6.3.1` inputs in the
candidate, the current checked-in `SDK-PACKAGE-MANIFEST.md` and runner map the
digests as follows:

| Artifact | SHA-256 | Evidence location |
|---|---|---|
| Action candidate Git commit | `fcbc44201b365daec4da85a4cf73adde8b82609c` (Git object ID) | `successor-fcbc442/candidate-sha.txt` |
| Candidate Git bundle | `87dd2441541bf5b12ff008947cc9c260561166cb521d14bb5ae6da3362dcdc92` | `successor-fcbc442/dependency-review-action.bundle` |
| Candidate `dist/` archive | `2520530a670ec30ba82f231233c365b65e5b3d8dabf89c6d79c7f98fd3ad8d98` | `successor-fcbc442/candidate-dist.tar.gz` |
| Source delta from the declared action base | `00ed5a720cb54e3bbc027489bb9a1faf376aceffad714b3be34f7816328de916` | `successor-fcbc442/action-candidate.patch.gz` |
| Registry tarball `@actions/artifact@6.3.1` | `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91` | `successor-fcbc442/sdk-patch/actions-artifact-6.3.1.tgz` |
| Build-time SDK patch | `b0d88441a5ccab1e699ee228ebbd4dec6888092aa2cc3db213b754f2f00d33ab` | `successor-fcbc442/sdk-patch/artifact-6.3.1-upload-subpath.patch` |
| Pristine full SDK package-tree manifest | `12c04ac3d8985d129e3b422e10877427943d61664eca2e2e7da3940025b63954` | `SDK-PACKAGE-MANIFEST.md`, `apply-artifact-sdk-patch.mjs` |
| Approved patched full SDK package-tree manifest | `d23e27f12cebcdbfb284be8f1a272fca5cc8a215d5b2ff6cc3a9fcc9dc0dca72` | `SDK-PACKAGE-MANIFEST.md`, `apply-artifact-sdk-patch.mjs` |
| Audit JSON inherited from candidate `4665869` | `439750bedcc8ac2919366ec298f3985c3ac7aaef5df31c54fd9f4da07001e2d0` | `evidence/action-build/npm-audit-final-466-inherited.json` |

The audit JSON and clean-build/dist evidence are inherited from `4665869`, not
freshly rerun for the test-only `fcbc442` commit. `SHA256SUMS` in the successor
directory contains the authoritative checksums for every listed package file.

## N3 — provenance, test count, and Jest warning

The `@nodable/entities@3.1.0` supplement maps package files to the upstream
repository's `Entity/` directory; the corresponding MIT `LICENSE` is at the
repository root. Its exact tag, commit, and file digest are recorded in
`successor-fcbc442/license-supplements/@nodable/entities-3.1.0/evidence.json`.

For `@protobuf-ts/runtime@2.11.1`, the package README assigns BSD-3-Clause to
`build/es2015/protobufjs-utf8.js` and `build/es2015/goog-varint.js`. The files
present in the generated bundle are the corresponding `build/commonjs/` files.
That path relationship is explanatory mapping metadata; the quoted upstream
license text is preserved verbatim and is not rewritten to change its paths.

The exact `fcbc442` test log records **16 suites and 217 tests**, run with
`--runInBand`. The separate `upload-only-prototype/PROTOTYPE-RESULTS.md` count
of 15 suites and 209 tests belongs to that earlier prototype's own run and is
not the `fcbc442` result. The sequential `fcbc442` log does not establish that a
parallel Jest worker warning is resolved. Harness reports the warning as
reproducible under parallel execution, with cause unknown; `--runInBand` is not
evidence about worker behavior.

## D1 — bundle history and replay boundary

The candidate bundle contains the `fcbc442…` ref and `git bundle verify`
succeeds. A clean clone from the bundle checks out the exact candidate SHA.
However, the source checkout used to make the bundle was shallow. The bundle
must not be described as the complete upstream repository history: a clone can
replay the candidate tree, but ancestry traversal beyond the available shallow
boundary is incomplete. In a clean clone, `git rev-list --count HEAD` fails at
`a1d282b36b6f3519aa1f3fc636f609c47dddb294` because parent commits
`454943c880b147adbfe7de0cdd3ece1c00882033` and
`eb6c199c5a85c7387f1f0b02b3ba5c6364740695` are absent; `git fsck --full
--no-reflogs` reports those missing commits. Use the bundle to recover the
candidate ref/tree, not to assert complete upstream ancestry. The separately
checksummed source patch and dist archive remain the package's replay materials.

## Workflow pin and decision boundary

The Showcase workflow patch remains pinned to the exact action commit
`fcbc44201b365daec4da85a4cf73adde8b82609c`. Harness reported applying it and
running the pin/ref tests on Showcase HEAD
`ff15af79c63ff174ea79bffaa2f36e802fb02e6c`; the patch is still not active.
The action candidate SHA did not change, so this documentation correction does
not require a new action SHA or a regenerated pin. No GitHub action run, upload,
private-repository 403 path, or live GHES server has been verified.

There is still no authorization to publish a fork or activate the workflow.
