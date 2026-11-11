<#
.SYNOPSIS
  Bat demo StyleMind bang mot lenh: docker compose + ngrok tunnel + kiem tra webhook SePay.

.DESCRIPTION
  1. Doc NGROK_DOMAIN tu bien moi truong hoac scripts/.demo.local (file nay nam trong .gitignore, KHONG commit).
  2. Kiem tra ngrok da cai va da co authtoken.
  3. docker compose up -d (file compose cua repo, khong kem mailpit), cho gateway :8080 healthy.
  4. Neu ngrok chua chay thi mo trong cua so PowerShell moi: ngrok http 8080 --url=https://$NGROK_DOMAIN
  5. Tu test POST {} vao <domain>/payment/internal/v1/sepay/webhooks: 401 = tunnel + gateway + payment-service thong.
  6. Khoi dong nong cac service va in tom tat.

  Dat domain mot lan (chon 1 cach):
    $env:NGROK_DOMAIN = 'ten-cua-ban.ngrok-free.dev'            # chi cho cua so PowerShell hien tai
    Set-Content scripts\.demo.local 'NGROK_DOMAIN=ten-cua-ban.ngrok-free.dev'   # luu lai (da bi gitignore)

  Chay:  powershell -ExecutionPolicy Bypass -File scripts\start-demo.ps1
  Tuy chon: -SkipNgrok (chi bat stack), -ProjectName stylemind
#>
[CmdletBinding()]
param(
  [switch]$SkipNgrok,
  [string]$ProjectName = 'stylemind'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $repo 'docker-compose.yml'
$localFile = Join-Path $PSScriptRoot '.demo.local'
$gateway = 'http://localhost:8080'
$script:problems = @()

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }
function Ok($text) { Write-Host "  [OK]   $text" -ForegroundColor Green }
function Warn($text) { Write-Host "  [CHU Y] $text" -ForegroundColor Yellow; $script:problems += $text }
function Stop-Demo($text) { Write-Host "`n  [DUNG] $text" -ForegroundColor Red; exit 1 }

# Tra ve HTTP status (0 = khong ket noi duoc). Khong nem loi khi 4xx/5xx.
function Get-HttpStatus([string]$Url, [string]$Method = 'GET', [string]$Body = $null, [hashtable]$Headers = @{}) {
  try {
    $params = @{ Uri = $Url; Method = $Method; UseBasicParsing = $true; TimeoutSec = 15; Headers = $Headers }
    if (-not [string]::IsNullOrEmpty($Body)) { $params.Body = $Body; $params.ContentType = 'application/json' }
    return [int](Invoke-WebRequest @params).StatusCode
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
    return 0
  } catch { return 0 }
}

# ---- 1. NGROK_DOMAIN -------------------------------------------------------------------------------
$domain = $env:NGROK_DOMAIN
if (-not $domain -and (Test-Path $localFile)) {
  $line = Get-Content $localFile | Where-Object { $_ -match '^\s*NGROK_DOMAIN\s*=' } | Select-Object -First 1
  if ($line) { $domain = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
}
if (-not $SkipNgrok) {
  if (-not $domain) {
    Stop-Demo ("Chua co NGROK_DOMAIN. Dat bang 1 trong 2 cach (khong commit domain):`n" +
      "    `$env:NGROK_DOMAIN = 'ten-cua-ban.ngrok-free.dev'`n" +
      "    Set-Content '$localFile' 'NGROK_DOMAIN=ten-cua-ban.ngrok-free.dev'   (file da nam trong .gitignore)")
  }
  $domain = $domain -replace '^https?://', '' -replace '/+$', ''
  if ($domain -notmatch '^[A-Za-z0-9.-]+$') { Stop-Demo 'NGROK_DOMAIN khong hop le (chi gom chu, so, dau cham, gach ngang; khong co https://).' }
}

# ---- 2. ngrok da cai + co authtoken ----------------------------------------------------------------
if (-not $SkipNgrok) {
  Step 'Kiem tra ngrok'
  if (-not (Get-Command ngrok -ErrorAction SilentlyContinue)) {
    Stop-Demo "Chua cai ngrok. Cai: winget install Ngrok.Ngrok   (hoac https://ngrok.com/download), roi chay lai."
  }
  $check = (& ngrok config check 2>&1 | Out-String).Trim()
  $cfgPath = if ($check -match 'Valid configuration file at (.+)') { $Matches[1].Trim() } else { $null }
  if (-not $cfgPath -or -not (Test-Path $cfgPath)) {
    Stop-Demo "ngrok chua co file cau hinh hop le. Chay: ngrok config add-authtoken <token>   (lay token tai https://dashboard.ngrok.com/get-started/your-authtoken)"
  }
  if (-not (Select-String -Path $cfgPath -Pattern '^\s*authtoken\s*:\s*\S+' -Quiet)) {
    Stop-Demo "ngrok chua co authtoken. Chay: ngrok config add-authtoken <token>   (lay token tai https://dashboard.ngrok.com/get-started/your-authtoken)"
  }
  Ok ("ngrok " + ((& ngrok version) -replace '^ngrok version ', '') + ", da co authtoken (khong in gia tri)")
}

# ---- 3. docker compose ----------------------------------------------------------------------------
Step 'Bat stack docker compose'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Stop-Demo 'Khong tim thay docker. Mo Docker Desktop roi chay lai.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { Stop-Demo 'Docker daemon chua chay. Mo Docker Desktop roi chay lai.' }
if (-not (Test-Path (Join-Path $repo '.env'))) { Warn 'Khong co file .env o thu muc BE: se dung gia tri mac dinh (thanh toan QR, gui email, anh chat co the khong hoat dong).' }
& docker compose -p $ProjectName -f $composeFile up -d
if ($LASTEXITCODE -ne 0) { Stop-Demo 'docker compose up -d that bai (xem loi o tren).' }

Write-Host '  Cho gateway :8080 healthy (toi da 3 phut)...'
$deadline = (Get-Date).AddMinutes(3)
$healthy = $false
while ((Get-Date) -lt $deadline) {
  if ((Get-HttpStatus "$gateway/actuator/health") -eq 200) { $healthy = $true; break }
  Start-Sleep -Seconds 3
}
if (-not $healthy) { Stop-Demo "Gateway $gateway chua healthy sau 3 phut. Xem: docker compose -p $ProjectName -f `"$composeFile`" ps / logs api-gateway" }
Ok "Gateway $gateway healthy"

# ---- 4. ngrok tunnel ------------------------------------------------------------------------------
$webhookUrl = $null
if (-not $SkipNgrok) {
  Step 'ngrok tunnel'
  $tunnelUrls = @()
  try { $tunnelUrls = @((Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 5).tunnels | ForEach-Object { $_.public_url }) } catch { }
  if ($tunnelUrls -contains "https://$domain") {
    Ok 'ngrok da chay dung domain nay - khong mo them'
  } elseif ($tunnelUrls.Count -gt 0) {
    Stop-Demo ("ngrok dang chay voi domain KHAC ($($tunnelUrls -join ', ')). Dong cua so ngrok do (hoac Stop-Process -Name ngrok) roi chay lai script.")
  } else {
    Start-Process powershell -ArgumentList '-NoExit', '-Command', "ngrok http 8080 --url=https://$domain"
    Ok "Da mo ngrok trong cua so PowerShell moi: ngrok http 8080 --url=https://$domain"
  }

  # ---- 5. tu test webhook ----
  $webhookUrl = "https://$domain/payment/internal/v1/sepay/webhooks"
  $status = 0
  for ($i = 0; $i -lt 15; $i++) {
    $status = Get-HttpStatus $webhookUrl 'POST' '{}' @{ 'ngrok-skip-browser-warning' = '1' }
    if ($status -ne 0 -and $status -lt 500 -and $status -ne 404) { break }
    Start-Sleep -Seconds 2
  }
  if ($status -eq 401) { Ok "Webhook qua ngrok -> 401 (dung mong doi: tunnel + gateway + payment-service thong, chu ky bi tu choi vi khong ky)" }
  elseif ($status -eq 0) { Warn "Khong ket noi duoc $webhookUrl - kiem tra cua so ngrok (loi authtoken/domain?) va http://127.0.0.1:4040" }
  elseif ($status -eq 404) { Warn "Webhook tra 404: tunnel toi duoc ngrok nhung khong thay endpoint (domain tro sai may/cong?)" }
  else { Warn "Webhook tra HTTP $status (mong doi 401). Neu 200: payment-service dang de AUTH_MODE=none - kiem tra STYLEMIND_SEPAY_WEBHOOK_AUTH_MODE=hmac trong .env." }
}

# ---- 6. khoi dong nong ----------------------------------------------------------------------------
Step 'Khoi dong nong'
$psJson = & docker compose -p $ProjectName -f $composeFile ps --format json 2>$null
$rows = @()
foreach ($line in ($psJson -split "`n")) { if ($line.Trim().StartsWith('{')) { try { $rows += ($line | ConvertFrom-Json) } catch { } } }
foreach ($row in $rows) {
  $name = $row.Service
  $state = "$($row.State)"; $health = "$($row.Health)"
  if ($state -eq 'running' -and ($health -eq '' -or $health -eq 'healthy')) { Ok "$name $state$(if ($health) { ' / ' + $health })" }
  else { Warn "$name $state $health" }
}
$seedPassword = if ($env:SEED_PASSWORD) { $env:SEED_PASSWORD } else { 'Stylemind@123' }   # mat khau demo cong khai, xem scripts/TEST_ACCOUNTS.md
try {
  $login = Invoke-RestMethod -Method Post -Uri "$gateway/identity/api/v1/auth/login" -ContentType 'application/json' `
    -Body (@{ email = 'an@sm.test'; password = $seedPassword } | ConvertTo-Json) -TimeoutSec 60
  if ($login.access_token) { Ok 'Dang nhap thu an@sm.test thanh cong (tai khoan seed)' }
  $auth = @{ Authorization = "Bearer $($login.access_token)" }
  [void](Get-HttpStatus "$gateway/commerce/api/v1/products?size=1")
  [void](Get-HttpStatus "$gateway/commerce/api/v1/orders?size=1" 'GET' $null $auth)
  [void](Get-HttpStatus "$gateway/wardrobe-community/api/v1/me/friends?size=1" 'GET' $null $auth)
  Ok 'Da goi thu commerce / wardrobe-community de lam nong'
} catch {
  Warn 'Dang nhap thu an@sm.test that bai: chua seed du lieu? Chay: node scripts/seed-test-data.cjs (xem scripts/TEST_ACCOUNTS.md).'
}

# ---- tom tat --------------------------------------------------------------------------------------
Step 'Tom tat'
$fe = if ((Get-HttpStatus 'http://localhost:3000') -ne 0) { 'http://localhost:3000' } else { 'http://localhost:3000  (CHUA chay: cd StyleMind_FE ; npm run dev)' }
$mail = if ((Get-HttpStatus 'http://localhost:8025') -ne 0) { 'http://localhost:8025' } else { '(khong chay - email OTP/loi moi di qua SMTP that cua .env)' }
Write-Host "  Giao dien (FE)     : $fe"
Write-Host "  Gateway            : $gateway"
Write-Host "  Mailpit            : $mail"
if (-not $SkipNgrok) {
  Write-Host "  ngrok inspector    : http://127.0.0.1:4040"
  Write-Host "  URL webhook SePay  : $webhookUrl"
}
if ($script:problems.Count -gt 0) {
  Write-Host "`n  Co $($script:problems.Count) muc can chu y (xem [CHU Y] o tren)." -ForegroundColor Yellow
} else {
  Write-Host "`n  San sang demo." -ForegroundColor Green
}
