# @actions/artifact 6.3.1 whole-package manifest

The candidate's build-only patch is applied to the exact locked registry
tarball. `npm ci --ignore-scripts` checks its SHA-512 SRI from
`package-lock.json`; the preserved tarball has SHA-256
`3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`.
The locked integrity is
`sha512-DBXgdB9T4yj2KSe0tFg+MTi9T5se+16bdrFQh+3Y6Nq1lZ3gYQk1BjHoTof+7Zf36bEAlfVMf+8ifRKeyGJugQ==`.

The deterministic manifest includes every directory and regular file path and
the SHA-256 of every file. Symlinks and other entry types are refused. The
whole-tree digest for the pristine package is
`0ed106103645a77c0728220c3b29b2bbb3f961327c86b3b3f72c4d9f732a81f0`; the
only accepted patched result is
`0582ee4ad2911d33b93c99981c8f23b96ffaf0283835f00ae262435c88aa38f4`.
The patch itself is SHA-256
`b0d88441a5ccab1e699ee228ebbd4dec6888092aa2cc3db213b754f2f00d33ab`.

The exact tarball was extracted into a clean tree, the patch applied, and its
result compared against the installed patched package. A mutation to
`lib/internal/upload/retention.js`—outside the five prior file guards—was
rejected before build. The runner then accepted the restored exact patched
tree. Full output is in `../evidence/action-build/sdk-integrity-mutation.log`.

Runner and manifest source: `apply-artifact-sdk-patch.mjs` and
`package-manifest.mjs`. These operate only during build. The generated action
contains neither patch application nor SDK download behavior.
