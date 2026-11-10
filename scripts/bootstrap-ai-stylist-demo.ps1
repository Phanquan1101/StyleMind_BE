[CmdletBinding()]
param(
    [ValidatePattern('^stylemind-ai-demo-[a-z0-9][a-z0-9-]{0,31}$')]
    [string]$ProjectName = 'stylemind-ai-demo-team',
    [ValidateRange(0, 50000)] [int]$PortOffset = 20000,
    [switch]$VerifyOffline,
    [switch]$ConfigureRootEnv,
    [switch]$StopApps
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
foreach ($tool in @('node', 'docker', 'java', 'mvn')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Thieu $tool trong PATH." }
}
$arguments = @((Join-Path $repoRoot 'stylist-service/scripts/team-demo-bootstrap.cjs'), '--project', $ProjectName, '--port-offset', "$PortOffset")
if ($VerifyOffline) { $arguments += '--verify-offline' }
if ($ConfigureRootEnv) { $arguments += '--configure-root-env' }
if ($StopApps) { $arguments += '--stop-apps' }
& node @arguments
if ($LASTEXITCODE -ne 0) { throw "Bootstrap that bai (exit $LASTEXITCODE). Xem loi va log duoc chi ra o tren." }
