export interface PackageManifestEntry {
  path: string
  type: 'directory' | 'file'
  sha256?: string
}

export function comparePathNames(left: string, right: string): number
export function packageManifest(packageRoot: string): PackageManifestEntry[]
export function packageTreeDigest(packageRoot: string): string
export function assertApprovedPackageTree(
  packageRoot: string,
  approvedTrees: {baseTreeSha256: string; patchedTreeSha256: string}
): 'base' | 'patched'
