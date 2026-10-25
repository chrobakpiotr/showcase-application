const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const http = require('node:http')
const {spawn} = require('node:child_process')

const action = '/private/tmp/dependency-review-action-v5.0.0-local/dist/index.js'
const base = '1'.repeat(40)
const head = 'a'.repeat(40)
const vulnerableChange = {
  change_type: 'added',
  manifest: 'package.json',
  ecosystem: 'npm',
  name: 'example',
  version: '1.0.0',
  package_url: 'pkg:npm/example@1.0.0',
  license: null,
  source_repository_url: null,
  vulnerabilities: [
    {
      severity: 'high',
      advisory_ghsa_id: 'GHSA-test-test-test',
      advisory_summary: 'controlled fixture advisory',
      advisory_url: 'https://example.test/advisory'
    }
  ]
}

async function run({failOnWarnings, statusCode = 200, warnings, body = []}) {
  let comparisonPath = ''
  const server = http.createServer((req, res) => {
    comparisonPath = req.url
    res.statusCode = statusCode
    res.setHeader('content-type', 'application/json')
    if (warnings) {
      res.setHeader(
        'x-github-dependency-graph-snapshot-warnings',
        Buffer.from(warnings).toString('base64')
      )
    }
    res.end(JSON.stringify(body))
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const {port} = server.address()
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'dependency-review-action-'))
  const summary = path.join(temp, 'summary.md')
  const output = path.join(temp, 'output')
  fs.writeFileSync(output, '')
  fs.writeFileSync(summary, '')
  fs.writeFileSync(path.join(temp, 'event.json'), '{}')
  const child = spawn(process.execPath, [action], {
    env: {
      ...process.env,
      GITHUB_ACTIONS: 'true',
      GITHUB_API_URL: `http://127.0.0.1:${port}`,
      GITHUB_EVENT_NAME: 'workflow_dispatch',
      GITHUB_EVENT_PATH: path.join(temp, 'event.json'),
      GITHUB_REPOSITORY: 'owner/repository',
      GITHUB_WORKSPACE: temp,
      GITHUB_STEP_SUMMARY: summary,
      GITHUB_OUTPUT: output,
      'INPUT_REPO-TOKEN': 'test-token',
      'INPUT_BASE-REF': base,
      'INPUT_HEAD-REF': head,
      'INPUT_FAIL-ON-SNAPSHOT-WARNINGS': String(failOnWarnings),
      'INPUT_FAIL-ON-SEVERITY': 'high',
      'INPUT_VULNERABILITY-CHECK': 'true',
      'INPUT_LICENSE-CHECK': 'false',
      'INPUT_SHOW-OPENSSF-SCORECARD': 'false'
    }
  })
  let allOutput = ''
  child.stdout.setEncoding('utf8').on('data', chunk => (allOutput += chunk))
  child.stderr.setEncoding('utf8').on('data', chunk => (allOutput += chunk))
  const status = await new Promise(resolve => child.on('close', resolve))
  await new Promise(resolve => server.close(resolve))
  fs.rmSync(temp, {recursive: true, force: true})
  return {status, output: allOutput, comparisonPath}
}

;(async () => {
  const results = {
    unresolvedWarningFailClosed: await run({
      failOnWarnings: true,
      warnings: 'snapshot unavailable'
    }),
    defaultModeKeepsUpstreamBehavior: await run({
      failOnWarnings: false,
      warnings: 'snapshot unavailable'
    }),
    existingHighSeverityGate: await run({
      failOnWarnings: true,
      body: [vulnerableChange]
    }),
    unavailableBaseApiFailure: await run({
      failOnWarnings: true,
      statusCode: 404,
      body: {message: 'Not Found'}
    }),
    apiPermissionFailure: await run({
      failOnWarnings: true,
      statusCode: 403,
      body: {message: 'Forbidden'}
    })
  }
  fs.writeFileSync(
    '/private/tmp/showcase-dependency-review-patch-evidence/built-action-results.json',
    JSON.stringify(results, null, 2)
  )
  if (
    results.unresolvedWarningFailClosed.status !== 1 ||
    results.defaultModeKeepsUpstreamBehavior.status !== 0 ||
    results.existingHighSeverityGate.status !== 1 ||
    results.unavailableBaseApiFailure.status !== 1 ||
    results.apiPermissionFailure.status !== 1 ||
    !results.unresolvedWarningFailClosed.comparisonPath.includes(`${base}...${head}`)
  ) {
    process.exitCode = 1
  }
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
