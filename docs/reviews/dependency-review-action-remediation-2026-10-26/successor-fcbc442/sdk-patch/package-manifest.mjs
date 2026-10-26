import {createHash} from 'node:crypto'
import {lstatSync, readFileSync, readdirSync} from 'node:fs'
import path from 'node:path'

export function comparePathNames(left, right) {
  if (left < right) return -1
  if (left > right) return 1
  return 0
}

export function assertApprovedPackageTree(packageRoot, approvedTrees) {
  const digest = packageTreeDigest(packageRoot)
  if (digest === approvedTrees.baseTreeSha256) return 'base'
  if (digest === approvedTrees.patchedTreeSha256) return 'patched'
  throw new Error(
    '@actions/artifact is neither the exact pristine package nor the exact patched package'
  )
}

export function packageManifest(packageRoot) {
  const entries = []
  function walk(directory, relative = '') {
    for (const entry of readdirSync(directory, {withFileTypes: true}).sort(
      (left, right) => comparePathNames(left.name, right.name)
    )) {
      const child = path.join(relative, entry.name).split(path.sep).join('/')
      const absolute = path.join(directory, entry.name)
      if (entry.isSymbolicLink()) {
        throw new Error(`Symlink in package tree: ${child}`)
      }
      if (entry.isDirectory()) {
        entries.push({path: child, type: 'directory'})
        walk(absolute, child)
      } else if (entry.isFile()) {
        const bytes = readFileSync(absolute)
        entries.push({
          path: child,
          type: 'file',
          sha256: createHash('sha256').update(bytes).digest('hex')
        })
      } else {
        throw new Error(`Unsupported package entry type: ${child}`)
      }
    }
  }
  const rootStat = lstatSync(packageRoot)
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) {
    throw new Error(`Package root is not a directory: ${packageRoot}`)
  }
  walk(packageRoot)
  return entries
}

export function packageTreeDigest(packageRoot) {
  const canonicalManifest = `${JSON.stringify(packageManifest(packageRoot))}\n`
  return createHash('sha256').update(canonicalManifest).digest('hex')
}
