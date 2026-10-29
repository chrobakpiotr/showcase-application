#!/usr/bin/env bash
set -euo pipefail
# Input is the unmodified historical package's bundle, already obtained from Showcase.
# Run: bash reconstruct.sh /absolute/path/to/dependency-review-action.bundle /new/output/dir
bundle=$(realpath "${1:?historical bundle path required}")
out=${2:?new output directory required}
test ! -e "$out"
printf '%s  %s\n' '87dd2441541bf5b12ff008947cc9c260561166cb521d14bb5ae6da3362dcdc92' "$bundle" | sha256sum -c -
mkdir -p "$out"
out=$(realpath "$out")
exec > >(tee "$out/reconstruction.log") 2>&1
git init --bare "$out/upstream.git"
git -C "$out/upstream.git" fetch https://github.com/actions/dependency-review-action.git a1d282b36b6f3519aa1f3fc636f609c47dddb294
git -C "$out/upstream.git" cat-file -e 454943c880b147adbfe7de0cdd3ece1c00882033^{commit}
git -C "$out/upstream.git" cat-file -e eb6c199c5a85c7387f1f0b02b3ba5c6364740695^{commit}
git clone "$bundle" "$out/candidate"
# Fetch negotiation may skip absent ancestors of an already present boundary commit.
# Copy the complete upstream pack, then independently validate every reachable object.
cp "$out/upstream.git"/objects/pack/* "$out/candidate/.git/objects/pack/"
test "$(git -C "$out/candidate" rev-parse HEAD)" = fcbc44201b365daec4da85a4cf73adde8b82609c
test "$(git -C "$out/candidate" rev-parse HEAD^{tree})" = 1c6d80541204acda361e169916136a9381ecaaa8
git -C "$out/candidate" fsck --full --no-reflogs
git -C "$out/candidate" bundle create "$out/dependency-review-action-full-history.bundle" HEAD
git -C "$out/candidate" bundle verify "$out/dependency-review-action-full-history.bundle"
git clone "$out/dependency-review-action-full-history.bundle" "$out/replay"
test "$(git -C "$out/replay" rev-parse HEAD)" = fcbc44201b365daec4da85a4cf73adde8b82609c
test "$(git -C "$out/replay" rev-parse HEAD^{tree})" = 1c6d80541204acda361e169916136a9381ecaaa8
test "$(git -C "$out/replay" rev-list --count HEAD)" = 1986
git -C "$out/replay" fsck --full --no-reflogs
test -z "$(git -C "$out/replay" status --porcelain)"
sha256sum "$out/dependency-review-action-full-history.bundle"
printf 'PASS: exact candidate and tree; 1986 commits; fresh replay fsck exit 0.\n'

