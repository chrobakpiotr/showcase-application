import {createHash} from 'node:crypto'
import {readFileSync} from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {spawnSync} from 'node:child_process'
import {
  assertApprovedPackageTree,
  packageTreeDigest
} from './package-manifest.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageRoot = path.join(root, 'node_modules/@actions/artifact')
const patchPath = path.join(root, 'patches/artifact-6.3.1-upload-subpath.patch')
const expected = {
  version: '6.3.1',
  resolved: 'https://registry.npmjs.org/@actions/artifact/-/artifact-6.3.1.tgz',
  integrity:
    'sha512-DBXgdB9T4yj2KSe0tFg+MTi9T5se+16bdrFQh+3Y6Nq1lZ3gYQk1BjHoTof+7Zf36bEAlfVMf+8ifRKeyGJugQ==',
  patchSha256:
    'b0d88441a5ccab1e699ee228ebbd4dec6888092aa2cc3db213b754f2f00d33ab',
  tarballSha256:
    '3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91',
  baseTreeSha256:
    '12c04ac3d8985d129e3b422e10877427943d61664eca2e2e7da3940025b63954',
  patchedTreeSha256:
    'd23e27f12cebcdbfb284be8f1a272fca5cc8a215d5b2ff6cc3a9fcc9dc0dca72'
}

const lock = JSON.parse(
  readFileSync(path.join(root, 'package-lock.json'), 'utf8')
)
const locked = lock.packages?.['node_modules/@actions/artifact']
if (
  locked?.version !== expected.version ||
  locked?.resolved !== expected.resolved ||
  locked?.integrity !== expected.integrity
) {
  throw new Error(
    'package-lock.json does not pin the expected @actions/artifact tarball'
  )
}

const manifest = JSON.parse(
  readFileSync(path.join(packageRoot, 'package.json'), 'utf8')
)
if (manifest.version !== expected.version) {
  throw new Error(
    `Expected @actions/artifact ${expected.version}, found ${manifest.version}`
  )
}

if (
  createHash('sha256').update(readFileSync(patchPath)).digest('hex') !==
  expected.patchSha256
) {
  throw new Error('Pinned @actions/artifact patch checksum mismatch')
}

const approvedTree = assertApprovedPackageTree(packageRoot, expected)
if (approvedTree === 'patched') {
  console.log('Verified the exact build-time @actions/artifact upload patch.')
  process.exit(0)
}

const relativePatch = path.relative(root, patchPath)
const check = spawnSync(
  'git',
  [
    'apply',
    '--check',
    '--directory=node_modules/@actions/artifact',
    relativePatch
  ],
  {cwd: root, encoding: 'utf8'}
)
if (check.status !== 0) {
  throw new Error(
    `SDK patch does not apply cleanly: ${check.stderr || check.stdout}`
  )
}
const apply = spawnSync(
  'git',
  ['apply', '--directory=node_modules/@actions/artifact', relativePatch],
  {cwd: root, encoding: 'utf8'}
)
if (apply.status !== 0) {
  throw new Error(
    `SDK patch application failed: ${apply.stderr || apply.stdout}`
  )
}
if (packageTreeDigest(packageRoot) !== expected.patchedTreeSha256) {
  throw new Error(
    'Patched @actions/artifact tree differs from the verified patch result'
  )
}
console.log(
  'Applied and verified the pinned build-time @actions/artifact upload patch.'
)
