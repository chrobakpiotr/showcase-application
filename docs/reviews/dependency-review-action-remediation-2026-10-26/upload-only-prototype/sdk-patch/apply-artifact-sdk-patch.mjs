import {createHash} from 'node:crypto'
import {existsSync, readFileSync} from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {spawnSync} from 'node:child_process'

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
  baseFiles: {
    'package.json':
      '11e27d680ad6960344214666ccf7d98d036ef6d5793fcc9bfafb6b60f9f45a35',
    'lib/internal/client.js':
      '11f78cd7b4b8e64fd95ed7b67fba8d2727c52df38b348ecc64835143e2844bd8',
    'lib/internal/upload/upload-artifact.js':
      '48e1f12b017550c1d6865f5310b33fd0374b2feffca3f951e54555535a5afcb5',
    'lib/internal/shared/config.js':
      '4dfeefbe961fb2f8fe493722133b5da659a822cb97799116e13069f964595645',
    'lib/internal/shared/errors.js':
      'dc6ffc76aa7b87ded7e3c53976c781ef5d50223d5c285985b7e7e0d156eeb4a9'
  },
  patchedFiles: {
    'package.json':
      'b6829fe000d47cbc6ba5f819b25c00132f9b84f4935dd1195d417d49118c0845',
    'lib/upload.js':
      '4ae65cdf3cffa23faab5b63d340c6fe7869a108397fff021fa888fb73acdf766',
    'lib/upload.d.ts':
      '3a1d34b6ed961364d7badfed0b990ff1f01f91466b65c143c7071873f35844e9'
  }
}

function digest(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex')
}

function assertHashes(files) {
  for (const [relative, expectedHash] of Object.entries(files)) {
    const file = path.join(packageRoot, relative)
    if (!existsSync(file) || digest(file) !== expectedHash) {
      throw new Error(`Unexpected @actions/artifact file: ${relative}`)
    }
  }
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

if (digest(patchPath) !== expected.patchSha256) {
  throw new Error('Pinned @actions/artifact patch checksum mismatch')
}

assertHashes({
  'lib/internal/client.js': expected.baseFiles['lib/internal/client.js'],
  'lib/internal/upload/upload-artifact.js':
    expected.baseFiles['lib/internal/upload/upload-artifact.js'],
  'lib/internal/shared/config.js':
    expected.baseFiles['lib/internal/shared/config.js'],
  'lib/internal/shared/errors.js':
    expected.baseFiles['lib/internal/shared/errors.js']
})

const patched = Object.entries(expected.patchedFiles).every(
  ([relative, hash]) => {
    const file = path.join(packageRoot, relative)
    return existsSync(file) && digest(file) === hash
  }
)
if (patched) {
  console.log('Verified the exact build-time @actions/artifact upload patch.')
  process.exit(0)
}

const base =
  digest(path.join(packageRoot, 'package.json')) ===
    expected.baseFiles['package.json'] &&
  !existsSync(path.join(packageRoot, 'lib/upload.js')) &&
  !existsSync(path.join(packageRoot, 'lib/upload.d.ts'))
if (!base) {
  throw new Error(
    '@actions/artifact is neither the exact pristine package nor the exact patched package'
  )
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
assertHashes(expected.patchedFiles)
console.log(
  'Applied and verified the pinned build-time @actions/artifact upload patch.'
)
