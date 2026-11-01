# Exact-candidate complete-history reconstruction — 2026-10-29

The historical successor bundle remains unchanged. It replays the candidate tree but omits upstream ancestry. This supplement reconstructs that ancestry without a new action commit or workflow pin.

## Inputs and observed result

- Showcase evidence source: `b46746a5362ece62281bbabd00cada67a4bfd12b`, sibling `successor-fcbc442/dependency-review-action.bundle`.
- Historical bundle SHA-256: `87dd2441541bf5b12ff008947cc9c260561166cb521d14bb5ae6da3362dcdc92`.
- Public upstream repository: `https://github.com/actions/dependency-review-action.git`; exact fetched commit: `a1d282b36b6f3519aa1f3fc636f609c47dddb294`.
- Recovered parent commits: `454943c880b147adbfe7de0cdd3ece1c00882033` and `eb6c199c5a85c7387f1f0b02b3ba5c6364740695`.
- Exact candidate: `fcbc44201b365daec4da85a4cf73adde8b82609c`; tree: `1c6d80541204acda361e169916136a9381ecaaa8`.
- Fresh replay reaches **1986 commits**; `git fsck --full --no-reflogs` exits 0; working tree is clean.

The original local reconstruction's additive complete-history bundle has SHA-256 `0c59a01238dd86a738c5264e90c304c0f1c17cc95de25b57e371ac91a12b4ab4`. Bundle bytes can vary with Git packing, so reconstruction verifies the exact candidate/tree and complete object connectivity rather than requiring that generated pack digest. The approximately 63 MiB generated bundle is deliberately excluded from this documentation commit.

## Reproduce

Obtain the historical bundle from the exact Showcase source above, then run:

```sh
bash reconstruct.sh /absolute/path/to/dependency-review-action.bundle /new/output/directory
```

The script verifies the original bundle checksum, fetches exact public upstream ancestry, imports its pack, creates an additive bundle, and independently clones and checks that bundle. It never writes to GitHub or changes the historical artifact. `reconstruction.log` records a successful execution of this script. `SHA256SUMS` covers this supplement.

This closes the reconstruction prerequisite for a future owner publication decision. It does not revise Harness's original **PASS WITH AMENDMENTS**, authorize publication/activation, or establish a real GitHub run, upload, private-repository 403 handling, GHES behavior, or resolution of the parallel Jest worker warning. The live [owner decision](../OWNER-DECISION.md) distinguishes the master's external-erratum disposition from the original reviewer verdict.
