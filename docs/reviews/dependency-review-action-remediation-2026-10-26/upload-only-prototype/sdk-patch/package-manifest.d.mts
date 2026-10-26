export interface PackageManifestEntry {
  path: string
  type: 'directory' | 'file'
  sha256?: string
}

export function packageManifest(packageRoot: string): PackageManifestEntry[]
export function packageTreeDigest(packageRoot: string): string
