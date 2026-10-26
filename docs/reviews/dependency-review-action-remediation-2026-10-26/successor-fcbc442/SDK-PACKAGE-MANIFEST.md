# `@actions/artifact@6.3.1` build input

This package is the exact registry tarball selected by `package-lock.json`:

- Resolved URL: `https://registry.npmjs.org/@actions/artifact/-/artifact-6.3.1.tgz`
- Locked SRI: `sha512-DBXgdB9T4yj2KSe0tFg+MTi9T5se+16bdrFQh+3Y6Nq1lZ3gYQk1BjHoTof+7Zf36bEAlfVMf+8ifRKeyGJugQ==`
- Preserved tarball SHA-256: `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`
- Patch SHA-256: `b0d88441a5ccab1e699ee228ebbd4dec6888092aa2cc3db213b754f2f00d33ab`
- Pristine whole-package manifest SHA-256: `12c04ac3d8985d129e3b422e10877427943d61664eca2e2e7da3940025b63954`
- Approved patched whole-package manifest SHA-256: `d23e27f12cebcdbfb284be8f1a272fca5cc8a215d5b2ff6cc3a9fcc9dc0dca72`

The manifest is deterministic over every directory and regular-file path, file content digest,
and entry type. It uses code-unit path ordering; symlinks, special entries, unexpected paths, and
unexpected contents are refused. The runner accepts only the exact pristine package or the exact
declared patched tree, then applies and verifies the patch. The patch adds only the build-time
`./upload` subpath and its types while retaining the SDK's existing upload implementation. The
generated action does not patch or download the SDK at runtime.

The values above match the candidate's `scripts/apply-artifact-sdk-patch.mjs`,
`scripts/package-manifest.mjs`, lockfile, patch, and preserved tarball. The package-local files and
candidate bundle are checksummed by this directory's `SHA256SUMS`.
