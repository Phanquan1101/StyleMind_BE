param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 3)]
    [int]$RunNumber
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
Set-Location -LiteralPath $repoRoot
$evidenceRoot = Join-Path $PSScriptRoot 'follow-up-runs\20261008T1405Z'
$runDir = Join-Path $evidenceRoot ('run-{0:D2}' -f $RunNumber)
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$started = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss.fffK')
$commit = (git rev-parse HEAD).Trim()
$manifest = foreach ($root in @('identity-service\src', 'notification-service\src')) {
    Get-ChildItem -LiteralPath $root -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = [System.IO.Path]::GetRelativePath($repoRoot, $_.FullName)
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        "$hash  $relative"
    }
}
$baselineManifest = Get-Content -LiteralPath (Join-Path $evidenceRoot 'source-test-sha256-before.txt')
$manifestDiff = Compare-Object -ReferenceObject $baselineManifest -DifferenceObject $manifest
$portConflicts = @(Get-NetTCPConnection -State Listen -LocalPort 8081, 8086, 8025 -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty LocalPort -Unique)
$containersBefore = @(docker ps --filter label=org.testcontainers=true --format '{{.Names}}')

$rawLog = Join-Path $runDir 'console.raw.tmp'
mvn -f .\identity-service\pom.xml verify *> $rawLog
$mavenExit = $LASTEXITCODE
$finished = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss.fffK')

$sourceReports = Join-Path $repoRoot 'identity-service\target\surefire-reports'
$archiveReports = Join-Path $runDir 'surefire-reports'
New-Item -ItemType Directory -Force -Path $archiveReports | Out-Null
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
$failedCases = [System.Collections.Generic.List[string]]::new()
foreach ($file in Get-ChildItem -LiteralPath $sourceReports -Filter 'TEST-*.xml' -File) {
    [xml]$doc = Get-Content -LiteralPath $file.FullName -Raw
    $suite = $doc.DocumentElement
    $tests += [int]$suite.tests
    $failures += [int]$suite.failures
    $errors += [int]$suite.errors
    $skipped += [int]$suite.skipped

    foreach ($case in $doc.SelectNodes('//testcase[failure or error]')) {
        $node = $case.SelectSingleNode('failure|error')
        $failedCases.Add("$($case.classname)#$($case.name) [$($node.Name):$($node.type)]")
    }

    foreach ($node in @($doc.SelectNodes('//system-out|//system-err'))) {
        [void]$node.ParentNode.RemoveChild($node)
    }
    foreach ($node in @($doc.SelectNodes('//failure|//error'))) {
        $node.InnerText = '[redacted diagnostic payload]'
        if ($node.Attributes['message']) {
            $node.Attributes['message'].Value = '[redacted]'
        }
    }

    $archivePath = Join-Path $archiveReports $file.Name
    $doc.Save($archivePath)
    $doc.Save($file.FullName)
}

$containersAfter = @(docker ps --filter label=org.testcontainers=true --format '{{.Names}}')
$safeLines = Get-Content -LiteralPath $rawLog | Where-Object {
    $_ -match '^\[INFO\].*(Scanning for projects|Building identity-service|--- .* @ identity-service ---|Tests run:|Results:|BUILD SUCCESS|BUILD FAILURE|Total time:|Finished at:)' -or
    $_ -match '^\[WARNING\] Tests run:' -or
    $_ -match '^\[ERROR\].*(Tests run:|Failed to execute goal|BUILD FAILURE)'
}
$logHeader = @(
    "Run: identity-verify-$('{0:D2}' -f $RunNumber)",
    "Started: $started",
    "Finished: $finished",
    "Commit baseline: $commit",
    "Source/test baseline unchanged: $(-not [bool]$manifestDiff)",
    "Source/test manifest entries: $($manifest.Count)",
    "Fixed-port conflict before run (8081/8086/8025): $([bool]$portConflicts)",
    "Testcontainers running before/after: $($containersBefore.Count)/$($containersAfter.Count)",
    "Maven exit code: $mavenExit",
    "Surefire totals: tests=$tests failures=$failures errors=$errors skipped=$skipped"
)
($logHeader + $safeLines) | Set-Content -LiteralPath (Join-Path $runDir 'console-redacted.log') -Encoding utf8

$summary = @(
    "# identity-verify-$('{0:D2}' -f $RunNumber)",
    '',
    "- Started: $started",
    "- Finished: $finished",
    "- Commit: $commit",
    '- Source/test baseline: see follow-up-runs/20261008T1405Z/source-test-sha256-before.txt',
    "- Source/test manifest unchanged: $(-not [bool]$manifestDiff)",
    "- Fixed-port conflicts (8081, 8086, 8025): $([bool]$portConflicts)",
    "- Testcontainers running before/after: $($containersBefore.Count)/$($containersAfter.Count)",
    "- Maven exit code: $mavenExit",
    "- Surefire: $tests tests, $failures failures, $errors errors, $skipped skipped",
    ''
)
if ($failedCases.Count) {
    $summary += 'Failures/errors:'
    $summary += $failedCases | ForEach-Object { "- $_" }
} else {
    $summary += 'Failures/errors: none'
}
$summary | Set-Content -LiteralPath (Join-Path $runDir 'run-summary.md') -Encoding utf8

Set-Content -LiteralPath $rawLog -Value '[raw console removed after redaction]' -Encoding utf8
Get-ChildItem -LiteralPath $sourceReports -Filter '*.txt' -File | ForEach-Object {
    Set-Content -LiteralPath $_.FullName -Value '[text report omitted; sanitized Surefire XML is archived]' -Encoding utf8
}

"RUN=identity-verify-$('{0:D2}' -f $RunNumber) EXIT=$mavenExit TESTS=$tests FAIL=$failures ERRORS=$errors SKIPPED=$skipped"
if ($portConflicts.Count) { "PORT_CONFLICTS=$($portConflicts -join ',')" } else { 'PORT_CONFLICTS=none' }
if ($manifestDiff) { 'SOURCE_BASELINE=CHANGED' } else { 'SOURCE_BASELINE=stable' }
if ($containersAfter.Count) { "TESTCONTAINERS_REMAINING=$($containersAfter -join ',')" } else { 'TESTCONTAINERS_REMAINING=none' }
