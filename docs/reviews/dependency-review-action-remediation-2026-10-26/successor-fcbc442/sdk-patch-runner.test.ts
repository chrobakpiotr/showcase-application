import {expect, test} from '@jest/globals'
import {
  cpSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync
} from 'node:fs'
import {spawnSync} from 'node:child_process'
import os from 'node:os'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {packageTreeDigest} from '../scripts/package-manifest.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function makeFixture(runnerSource?: string) {
  const temporary = mkdtempSync(path.join(os.tmpdir(), 'sdk-runner-'))
  const fixtureRoot = path.join(temporary, 'candidate')
  const artifactRoot = path.join(fixtureRoot, 'node_modules/@actions/artifact')
  mkdirSync(path.dirname(artifactRoot), {recursive: true})
  cpSync(path.join(root, 'node_modules/@actions/artifact'), artifactRoot, {
    recursive: true
  })
  cpSync(
    path.join(root, 'package-lock.json'),
    path.join(fixtureRoot, 'package-lock.json')
  )
  mkdirSync(path.join(fixtureRoot, 'patches'), {recursive: true})
  cpSync(
    path.join(root, 'patches/artifact-6.3.1-upload-subpath.patch'),
    path.join(fixtureRoot, 'patches/artifact-6.3.1-upload-subpath.patch')
  )
  mkdirSync(path.join(fixtureRoot, 'scripts'), {recursive: true})
  cpSync(
    path.join(root, 'scripts/package-manifest.mjs'),
    path.join(fixtureRoot, 'scripts/package-manifest.mjs')
  )
  const patchPath = path.join(
    fixtureRoot,
    'patches/artifact-6.3.1-upload-subpath.patch'
  )
  const tree = spawnSync(
    process.execPath,
    [
      '--input-type=module',
      '-e',
      `
      import {packageTreeDigest} from './scripts/package-manifest.mjs'
      process.stdout.write(packageTreeDigest('./node_modules/@actions/artifact'))
    `
    ],
    {cwd: fixtureRoot, encoding: 'utf8'}
  )
  if (tree.status !== 0)
    throw new Error(`Could not inspect SDK fixture: ${tree.stderr}`)
  if (
    tree.stdout ===
    'd23e27f12cebcdbfb284be8f1a272fca5cc8a215d5b2ff6cc3a9fcc9dc0dca72'
  ) {
    const reverse = spawnSync(
      'git',
      [
        'apply',
        '--reverse',
        '--check',
        '--directory=node_modules/@actions/artifact',
        patchPath
      ],
      {cwd: fixtureRoot, encoding: 'utf8'}
    )
    if (reverse.status !== 0)
      throw new Error(
        `Could not restore pristine SDK fixture: ${reverse.stderr}`
      )
    const applyReverse = spawnSync(
      'git',
      [
        'apply',
        '--reverse',
        '--directory=node_modules/@actions/artifact',
        patchPath
      ],
      {cwd: fixtureRoot, encoding: 'utf8'}
    )
    if (applyReverse.status !== 0)
      throw new Error(
        `Could not restore pristine SDK fixture: ${applyReverse.stderr}`
      )
  } else if (
    tree.stdout !==
    '12c04ac3d8985d129e3b422e10877427943d61664eca2e2e7da3940025b63954'
  ) {
    throw new Error(
      `Candidate SDK tree is not an approved fixture: ${tree.stdout}`
    )
  }
  const runnerPath = path.join(
    fixtureRoot,
    'scripts/apply-artifact-sdk-patch.mjs'
  )
  writeFileSync(
    runnerPath,
    runnerSource ??
      readFileSync(
        path.join(root, 'scripts/apply-artifact-sdk-patch.mjs'),
        'utf8'
      )
  )
  return {temporary, fixtureRoot, artifactRoot, runnerPath}
}

function runRunner(runnerPath: string, cwd: string) {
  return spawnSync(process.execPath, [runnerPath], {cwd, encoding: 'utf8'})
}

function expectPrePatchRefusal(
  result: ReturnType<typeof runRunner>,
  packageJsonBefore: Buffer,
  packageJsonPath: string
) {
  expect(result.status).not.toBe(0)
  expect(`${result.stdout}${result.stderr}`).not.toMatch(
    /Verified|Applied and verified/
  )
  expect(readFileSync(packageJsonPath)).toEqual(packageJsonBefore)
}

test('the real SDK patch runner rejects an out-of-guard mutation before patching', () => {
  const fixture = makeFixture()
  try {
    const packageJsonPath = path.join(fixture.artifactRoot, 'package.json')
    const packageJsonBefore = readFileSync(packageJsonPath)
    const mutatedFile = path.join(
      fixture.artifactRoot,
      'lib/internal/upload/retention.js'
    )
    const original = readFileSync(mutatedFile)
    writeFileSync(
      mutatedFile,
      Buffer.concat([
        original,
        Buffer.from('\nmutation outside former guards\n')
      ])
    )

    const mutatedTree = packageTreeDigest(fixture.artifactRoot)
    const result = runRunner(fixture.runnerPath, fixture.fixtureRoot)
    expectPrePatchRefusal(result, packageJsonBefore, packageJsonPath)
    expect(`${result.stdout}${result.stderr}`).toContain(
      'neither the exact pristine package nor the exact patched package'
    )
    expect(packageTreeDigest(fixture.artifactRoot)).toBe(mutatedTree)

    const runnerSource = readFileSync(fixture.runnerPath, 'utf8')
    const guardCall =
      'const approvedTree = assertApprovedPackageTree(packageRoot, expected)'
    expect(runnerSource).toContain(guardCall)
    const guardDisabledSource = runnerSource.replace(
      guardCall,
      'const approvedTree = "base"'
    )
    expect(guardDisabledSource).not.toBe(runnerSource)
    const mutant = makeFixture(guardDisabledSource)
    try {
      const mutantOutsideGuard = path.join(
        mutant.artifactRoot,
        'lib/internal/upload/retention.js'
      )
      const mutantOriginal = readFileSync(mutantOutsideGuard)
      writeFileSync(
        mutantOutsideGuard,
        Buffer.concat([
          mutantOriginal,
          Buffer.from('\nmutation outside former guards\n')
        ])
      )
      const mutantTreeBefore = packageTreeDigest(mutant.artifactRoot)
      const mutantResult = runRunner(mutant.runnerPath, mutant.fixtureRoot)
      expect(mutantResult.status).not.toBe(0)
      expect(`${mutantResult.stdout}${mutantResult.stderr}`).not.toMatch(
        /Verified|Applied and verified/
      )
      expect(packageTreeDigest(mutant.artifactRoot)).not.toBe(mutantTreeBefore)
    } finally {
      rmSync(mutant.temporary, {recursive: true, force: true})
    }
  } finally {
    rmSync(fixture.temporary, {recursive: true, force: true})
  }
})
