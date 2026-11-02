param([string]$Directory = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath($Directory)
$envPath = Join-Path $destination '.env'
$keyPath = Join-Path $destination '.secrets/stylemind-identity-private.pem'
if ((Test-Path -LiteralPath $envPath) -or (Test-Path -LiteralPath $keyPath)) {
    throw 'Existing .env or signing key found. Keep existing credentials; follow the upgrade section in the team guide.'
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install JDK 21 and add java to PATH first.' }
$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '../.env.example'))
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
function New-LocalSecret([bool]$Hex = $false) {
    $buffer = New-Object byte[] 32
    $rng.GetBytes($buffer)
    if ($Hex) { return ([BitConverter]::ToString($buffer)).Replace('-', '').ToLowerInvariant() }
    return [Convert]::ToBase64String($buffer)
}
try {
    $values = @{
        STYLEMIND_POSTGRES_ADMIN_USERNAME = 'stylemind_local_admin'
        STYLEMIND_VERIFICATION_HMAC_SECRET = (New-LocalSecret)
        STYLEMIND_SERVICE_TOKEN = (New-LocalSecret)
        STYLEMIND_COMMERCE_PAYMENT_TOKEN = (New-LocalSecret)
        STYLEMIND_COMMERCE_SERVICE_TOKEN = (New-LocalSecret)
        STYLEMIND_PAYMENT_EVENTS_ENABLED = 'true'
        STYLEMIND_SEPAY_WEBHOOK_SECRET = (New-LocalSecret)
    }
    foreach ($scope in @('POSTGRES_ADMIN', 'IDENTITY_DB', 'COMMERCE_DB', 'PAYMENT_DB', 'WARDROBE_COMMUNITY_DB', 'STYLIST_DB', 'NOTIFICATION_DB')) {
        $values["STYLEMIND_${scope}_PASSWORD"] = New-LocalSecret $true
    }
    foreach ($entry in $values.GetEnumerator()) {
        $pattern = '(?m)^' + [regex]::Escape($entry.Key) + '=.*$'
        if (-not [regex]::IsMatch($template, $pattern)) { throw "Missing template variable: $($entry.Key)" }
        $value = [string]$entry.Value
        $template = [regex]::Replace($template, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($match) $match.Value.Split('=')[0] + '=' + $value })
    }
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    & java (Join-Path $PSScriptRoot 'GenerateLocalIdentityKey.java') $keyPath
    if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed; .env was not written.' }
    $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($template)
    $stream = [IO.File]::Open($envPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Write-Output 'Created local .env and signing key. No credentials printed. Uses Mailpit and synthetic bank settings.'
    Write-Output 'For real bank testing, set your SePay credential and bank account in .env; see docs/setup/US-04.02_TEAM_SETUP.md.'
} finally { $rng.Dispose() }
