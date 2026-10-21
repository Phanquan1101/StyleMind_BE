$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
Set-Location -LiteralPath $repoRoot
$reportPath = Join-Path $PSScriptRoot 'disposable-recovery-summary.md'
$overridePath = Join-Path $PSScriptRoot 'disposable-notification-port.override.yml'
$project = 'stylemind-identity-audit-20261008-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$identityUrl = 'http://127.0.0.1:8081'
$notificationUrl = 'http://127.0.0.1:18086'
$mailpitUrl = 'http://127.0.0.1:8025'
$composeBase = @('--project-name', $project, '--file', '.\docker-compose.yml', '--file', $overridePath)
$checks = [System.Collections.Generic.List[string]]::new()
$observations = [System.Collections.Generic.List[string]]::new()
$stage = 'preflight'
$stackStarted = $false
$cleanupStatus = 'not-needed'
$startedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss.fffK')
$recoverySeconds = $null
$failureType = $null

function Add-Check([string]$Name, [bool]$Passed) {
    if (-not $Passed) { throw "AUDIT_CHECK_FAILED:$Name" }
    $script:checks.Add($Name)
}

function Read-DotEnvValue([string]$Name) {
    $line = Get-Content -LiteralPath '.env' | Where-Object { $_ -match ('^' + [regex]::Escape($Name) + '=') } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return ($line -split '=', 2)[1]
}

function Invoke-Compose([string[]]$Arguments) {
    $base = $script:composeBase
    $output = & docker compose @base @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'AUDIT_COMPOSE_COMMAND_FAILED' }
    return $output
}

function Get-ServiceContainerId([string]$Service) {
    $result = @(Invoke-Compose @('ps', '-q', $Service))
    if ($result.Count -ne 1 -or -not $result[0]) { throw "AUDIT_CONTAINER_NOT_FOUND:$Service" }
    return $result[0].Trim()
}

function Invoke-Http([string]$Method, [string]$Uri, $Body = $null, [hashtable]$Headers = @{}) {
    $parameters = @{ Uri = $Uri; Method = $Method; TimeoutSec = 15; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
    }
    if ($Headers.Count -gt 0) { $parameters.Headers = $Headers }
    $response = Invoke-WebRequest @parameters
    $json = $null
    if ($response.Content) {
        try { $json = ConvertFrom-Json -InputObject $response.Content -AsHashtable } catch { }
    }
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $json }
}

function Wait-HttpHealth([string]$Uri, [string]$ServiceName, [int]$TimeoutSeconds = 150) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-RestMethod -Uri $Uri -TimeoutSec 5
            if ($response.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "AUDIT_HEALTH_TIMEOUT:$ServiceName"
}

function Wait-Mailpit([int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try { Invoke-RestMethod -Uri ($script:mailpitUrl + '/api/v1/messages?limit=1') -TimeoutSec 5 | Out-Null; return } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'AUDIT_MAILPIT_TIMEOUT'
}

function Get-MailMessages {
    $result = Invoke-RestMethod -Uri ($script:mailpitUrl + '/api/v1/messages?limit=100') -TimeoutSec 10
    return @($result.messages)
}

function Get-MailCount([string]$Address) {
    $count = 0
    foreach ($message in Get-MailMessages) {
        $recipients = @($message.To | ForEach-Object { $_.Address })
        if ($recipients -contains $Address) { $count++ }
    }
    return $count
}

function Get-Otp([string]$Address) {
    foreach ($message in Get-MailMessages) {
        $recipients = @($message.To | ForEach-Object { $_.Address })
        if ($recipients -notcontains $Address) { continue }
        $detail = Invoke-RestMethod -Uri ($script:mailpitUrl + '/api/v1/message/' + $message.ID) -TimeoutSec 10
        $mailText = ([string]$detail.Text) + "`n" + ([string]$detail.HTML)
        $match = [regex]::Match($mailText, '(?<!\d)\d{6}(?!\d)')
        if ($match.Success) { return $match.Value }
    }
    throw 'AUDIT_MAIL_OTP_NOT_FOUND'
}

function Invoke-Psql([string]$Database, [string]$Sql) {
    $base = $script:composeBase
    $output = & docker compose @base exec -T postgres psql -X -v ON_ERROR_STOP=1 -U $script:adminUser -d $Database -At -F '|' -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'AUDIT_DATABASE_QUERY_FAILED' }
    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Get-ContainerHealth([string]$ContainerId) {
    $json = & docker inspect $ContainerId 2>$null
    if ($LASTEXITCODE -ne 0) { return 'missing' }
    $item = ConvertFrom-Json -InputObject ($json -join "`n")
    if ($null -eq $item[0].State.Health) { return $item[0].State.Status }
    return $item[0].State.Health.Status
}

try {
    $smtpLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^STYLEMIND_SMTP_HOST=' } | Select-Object -First 1
    $smtpParts = $smtpLine -split '=', 2
    $defaultSmtpIsMailpit = $smtpParts.Count -eq 2 -and $smtpParts[1] -eq 'mailpit'
    Add-Check 'Default .env SMTP target was inspected without printing it; external/non-Mailpit configuration remains isolated from this run' (-not $defaultSmtpIsMailpit)
    $secretText = Read-DotEnvValue 'STYLEMIND_VERIFICATION_HMAC_SECRET'
    $decodedSecret = [Convert]::FromBase64String($secretText)
    Add-Check 'Local HMAC is valid Base64 and decodes to 32 bytes' ($decodedSecret.Length -eq 32)
    $script:adminUser = Read-DotEnvValue 'STYLEMIND_POSTGRES_ADMIN_USERNAME'
    Add-Check 'Database administrator username is configured' (-not [string]::IsNullOrWhiteSpace($script:adminUser))
    $serviceToken = Read-DotEnvValue 'STYLEMIND_SERVICE_TOKEN'
    Add-Check 'Internal service token is configured' (-not [string]::IsNullOrWhiteSpace($serviceToken))

    $occupied = @(Get-NetTCPConnection -State Listen -LocalPort 8081, 18086, 8025 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty LocalPort -Unique)
    Add-Check 'Disposable host ports 8081/18086/8025 are available' ($occupied.Count -eq 0)
    $existing = @(& docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.Names}}')
    Add-Check 'Unique Compose project has no pre-existing containers' ($existing.Count -eq 0 -or -not $existing[0])

    $env:STYLEMIND_SMTP_HOST = 'mailpit'
    $env:STYLEMIND_SMTP_PORT = '1025'
    $env:STYLEMIND_SMTP_USERNAME = ''
    $env:STYLEMIND_SMTP_PASSWORD = ''
    $env:STYLEMIND_SMTP_AUTH = 'false'
    $env:STYLEMIND_SMTP_STARTTLS = 'false'
    $env:STYLEMIND_EMAIL_FROM = 'no-reply@stylemind.local'
    $env:STYLEMIND_IDENTITY_DB_URL = 'jdbc:postgresql://postgres:5432/stylemind_identity'
    $env:STYLEMIND_NOTIFICATION_DB_URL = 'jdbc:postgresql://postgres:5432/stylemind_notification'

    $stage = 'start-disposable-compose'
    $null = Invoke-Compose @('up', '-d', '--build', 'postgres', 'mailpit', 'notification-service', 'identity-service')
    $stackStarted = $true
    Write-Output 'DISPOSABLE_COMPOSE=up'
    Wait-HttpHealth ($identityUrl + '/actuator/health') 'identity-service'
    Write-Output 'IDENTITY_HEALTH=UP'
    Wait-HttpHealth ($notificationUrl + '/actuator/health') 'notification-service'
    Write-Output 'NOTIFICATION_HEALTH=UP'
    Wait-Mailpit
    Write-Output 'MAILPIT_API=UP'
    Add-Check 'Disposable Identity, Notification and Mailpit reached readiness' $true

    $runId = [guid]::NewGuid().ToString('N')
    $emailA = "audit-$runId-a@example.test"
    $emailB = "audit-$runId-b@example.test"
    $wrongRecipient = "audit-$runId-internal-spoof@example.test"
    $internalRecipient = "audit-$runId-internal@example.test"
    $passwordA = 'Audit-old-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $passwordANew = 'Audit-new-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $passwordB = 'Audit-user-' + [guid]::NewGuid().ToString('N').Substring(0, 12)

    $stage = 'probe-email-and-password-boundaries'
    $overlongEmail = ('a' * 64) + '@' + ('b' * 63) + '.' + ('c' * 63) + '.' + ('d' * 62)
    $accountsBeforeLongForgot = Invoke-Psql 'stylemind_identity' 'SELECT count(*) FROM account'
    $mailBeforeLongForgot = @(Get-MailMessages).Count
    $forgotLongEmail = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/forgot-password') @{ email = $overlongEmail }
    $accountsAfterLongForgot = Invoke-Psql 'stylemind_identity' 'SELECT count(*) FROM account'
    $mailAfterLongForgot = @(Get-MailMessages).Count
    $observations.Add("Identity forgot-password with a syntactically structured 255-character email returned HTTP $($forgotLongEmail.Status); account count delta=$([int]$accountsAfterLongForgot - [int]$accountsBeforeLongForgot), Mailpit message delta=$($mailAfterLongForgot - $mailBeforeLongForgot)")
    $overlongRegister = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/register') @{ email = $overlongEmail; password = $passwordA }
    $longEmailAccountCount = Invoke-Psql 'stylemind_identity' "SELECT count(*) FROM account WHERE email = '$overlongEmail'"
    $longEmailMailCount = Get-MailCount $overlongEmail
    $observations.Add("Identity register with the same 255-character email returned HTTP $($overlongRegister.Status); matching account rows=$longEmailAccountCount, Mailpit messages=$longEmailMailCount; DTO/schema define no explicit email maximum")

    $boundaryEmail = "audit-$runId-72byte@example.test"
    $password72 = 'P' * 72
    $boundaryRegistration = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/register') @{ email = $boundaryEmail; password = $password72 }
    $boundaryAccountCount = Invoke-Psql 'stylemind_identity' "SELECT count(*) FROM account WHERE email = '$boundaryEmail'"
    $boundaryHashCheck = Invoke-Psql 'stylemind_identity' "SELECT (password_hash <> '$password72')::text FROM account WHERE email = '$boundaryEmail'"
    Add-Check 'Exactly 72 UTF-8 bytes is accepted and only a non-plaintext hash is stored' ($boundaryRegistration.Status -eq 201 -and $boundaryAccountCount -eq '1' -and $boundaryHashCheck -eq 'true')

    $tooLongEmail = "audit-$runId-73byte@example.test"
    $password73 = 'P' * 73
    $accountCountBefore73 = Invoke-Psql 'stylemind_identity' 'SELECT count(*) FROM account'
    $mailCountBefore73 = @(Get-MailMessages).Count
    $tooLongPasswordRegistration = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/register') @{ email = $tooLongEmail; password = $password73 }
    $accountCountAfter73 = Invoke-Psql 'stylemind_identity' 'SELECT count(*) FROM account'
    $mailCountAfter73 = @(Get-MailMessages).Count
    Add-Check '73-byte Register password is rejected before database or email side effects' ($tooLongPasswordRegistration.Status -eq 400 -and $accountCountAfter73 -eq $accountCountBefore73 -and $mailCountAfter73 -eq $mailCountBefore73)

    $stage = 'register-account-a'
    $registerA = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/register') @{
        email = $emailA; password = $passwordA; account_id = [guid]::NewGuid().ToString()
        role = 'ADMIN'; roles = @('ADMIN'); account_status = 'ACTIVE'
        email_verified_at = '2099-01-01T00:00:00Z'; password_hash = 'forged'
        recipient_email = $emailB; expires_at = '2099-01-01T00:00:00Z'
        failed_attempts = 0; revoked_at = $null
    }
    Add-Check 'Register A returned 201 and remains pending despite forged fields' ($registerA.Status -eq 201 -and $registerA.Json.account_status -eq 'PENDING_VERIFICATION')
    $accountA = [string]$registerA.Json.account_id
    $roleA = Invoke-Psql 'stylemind_identity' "SELECT string_agg(role_code, ',') FROM account_role WHERE account_id = '$accountA'"
    $stateA = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text FROM account WHERE account_id = '$accountA'"
    Add-Check 'Register A database state is pending/unverified and role is CUSTOMER' ($stateA -eq 'PENDING_VERIFICATION|false' -and $roleA -eq 'CUSTOMER')
    Add-Check 'Register response has no password/hash or verification-code fields' (-not (@($registerA.Json.Keys) | Where-Object { $_ -match 'password|hash|code|token' }))
    $otpA = Get-Otp $emailA
    Add-Check 'Mailpit received one verification message for A' ((Get-MailCount $emailA) -eq 1)

    $stage = 'register-account-b'
    $registerB = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/register') @{ email = $emailB; password = $passwordB }
    Add-Check 'Register B returned pending account' ($registerB.Status -eq 201 -and $registerB.Json.account_status -eq 'PENDING_VERIFICATION')
    $accountB = [string]$registerB.Json.account_id
    $otpB = Get-Otp $emailB
    Add-Check 'Mailpit received one verification message for B' ((Get-MailCount $emailB) -eq 1)

    $stage = 'verify-account-a'
    $verifyA = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/verify-email') @{
        email = $emailA; code = $otpA; account_id = $accountB; role = 'ADMIN'
        account_status = 'ACTIVE'; email_verified_at = '2099-01-01T00:00:00Z'
        expires_at = '2099-01-01T00:00:00Z'; failed_attempts = 0; revoked_at = $null
    }
    Add-Check 'A verification succeeded with unknown fields ignored' ($verifyA.Status -eq 204)
    $stateA = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text FROM account WHERE account_id = '$accountA'"
    $stateB = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text FROM account WHERE account_id = '$accountB'"
    $roleA = Invoke-Psql 'stylemind_identity' "SELECT string_agg(role_code, ',') FROM account_role WHERE account_id = '$accountA'"
    Add-Check 'Forged verification fields affected only A and did not change either CUSTOMER role' ($stateA -eq 'ACTIVE|true' -and $stateB -eq 'PENDING_VERIFICATION|false' -and $roleA -eq 'CUSTOMER')

    if ($otpA -ne $otpB) {
        $crossVerify = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/verify-email') @{ email = $emailB; code = $otpA }
        $stateB = Invoke-Psql 'stylemind_identity' "SELECT account_status FROM account WHERE account_id = '$accountB'"
        Add-Check 'A verification code cannot verify B' ($crossVerify.Status -eq 400 -and $stateB -eq 'PENDING_VERIFICATION')
    } else {
        $checks.Add('Cross-account email OTP probe not run because the independently generated six-digit codes collided')
    }

    $stage = 'login-account-a'
    $loginA = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/login') @{ email = $emailA; password = $passwordA }
    Add-Check 'Verified A can login before restart' ($loginA.Status -eq 200 -and $loginA.Json.access_token -and $loginA.Json.refresh_token)
    $accessA = [string]$loginA.Json.access_token
    $refreshA = [string]$loginA.Json.refresh_token
    Add-Check 'Login response does not expose password_hash' (-not ($loginA.Json.Keys -contains 'password_hash'))
    $jwksBefore = Invoke-Http 'GET' ($identityUrl + '/.well-known/jwks.json')
    Add-Check 'JWKS is available before restart' ($jwksBefore.Status -eq 200 -and $jwksBefore.Json.keys.Count -gt 0)
    $firstJwk = $jwksBefore.Json.keys[0]
    $publicKeyMarkerBefore = [string]$firstJwk.kid + '|' + [string]$firstJwk.n

    $stage = 'create-reset-and-pending-challenges'
    $forgotA = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/forgot-password') @{ email = $emailA }
    Add-Check 'Forgot A returned generic accepted response' ($forgotA.Status -eq 202)
    $resetOtp = Get-Otp $emailA
    $resetMessagesBeforeRestart = Get-MailCount $emailA
    Add-Check 'A has one reset email in Mailpit' ($resetMessagesBeforeRestart -eq 2)
    $flywayBeforeRestart = Invoke-Psql 'stylemind_identity' "SELECT string_agg(version || ':' || success::text, ',' ORDER BY installed_rank) FROM flyway_schema_history"
    Add-Check 'Disposable schema has successful Flyway V1-V3 only' ($flywayBeforeRestart -eq '1:true,2:true,3:true')

    $stage = 'restart-identity'
    $identityBefore = Get-ServiceContainerId 'identity-service'
    $null = Invoke-Compose @('restart', 'identity-service')
    Wait-HttpHealth ($identityUrl + '/actuator/health') 'identity-service'
    $identityAfter = Get-ServiceContainerId 'identity-service'
    Add-Check 'Identity container restarted and returned healthy' ($identityBefore -eq $identityAfter)
    $jwksAfter = Invoke-Http 'GET' ($identityUrl + '/.well-known/jwks.json')
    $publicKeyAfter = [string]$jwksAfter.Json.keys[0].kid + '|' + [string]$jwksAfter.Json.keys[0].n
    Add-Check 'RSA public JWK stayed stable across Identity restart' ($jwksAfter.Status -eq 200 -and $publicKeyAfter -eq $publicKeyMarkerBefore)
    $anonymousProbe = Invoke-Http 'GET' ($identityUrl + '/api/v1/audit-protected-probe')
    $jwtProbe = Invoke-Http 'GET' ($identityUrl + '/api/v1/audit-protected-probe') $null @{ Authorization = "Bearer $accessA" }
    Add-Check 'Pre-restart JWT is accepted after restart (protected missing route returns 404, anonymous returns 401)' ($anonymousProbe.Status -eq 401 -and $jwtProbe.Status -eq 404)
    $refreshAfterRestart = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/refresh') @{ refresh_token = $refreshA }
    Add-Check 'Pre-restart refresh session survived Identity restart and rotated' ($refreshAfterRestart.Status -eq 200 -and $refreshAfterRestart.Json.refresh_token)
    $refreshA2 = [string]$refreshAfterRestart.Json.refresh_token

    $stage = 'verify-pending-b-and-reset-a-challenge'
    $verifyB = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/verify-email') @{ email = $emailB; code = $otpB }
    Add-Check 'B verification challenge remained usable after Identity restart' ($verifyB.Status -eq 204)
    $stateB = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text FROM account WHERE account_id = '$accountB'"
    Add-Check 'B became active only after its own OTP' ($stateB -eq 'ACTIVE|true')
    $loginB = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/login') @{ email = $emailB; password = $passwordB }
    Add-Check 'B login created a separate session before A reset' ($loginB.Status -eq 200 -and $loginB.Json.refresh_token)
    $refreshB = [string]$loginB.Json.refresh_token
    $verifiedReset = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/verify-password-reset') @{ email = $emailA; code = $resetOtp }
    Add-Check 'A reset challenge remained usable after Identity restart' ($verifiedReset.Status -eq 200 -and $verifiedReset.Json.reset_token)
    $resetToken = [string]$verifiedReset.Json.reset_token

    $stage = 'restart-notification-and-check-internal-auth'
    $mailCountBeforeNotificationRestart = @(Get-MailMessages).Count
    $notificationBefore = Get-ServiceContainerId 'notification-service'
    $null = Invoke-Compose @('restart', 'notification-service')
    Wait-HttpHealth ($notificationUrl + '/actuator/health') 'notification-service'
    $notificationAfter = Get-ServiceContainerId 'notification-service'
    Add-Check 'Notification container restarted and returned healthy' ($notificationBefore -eq $notificationAfter)
    $mailCountAfterNotificationRestart = @(Get-MailMessages).Count
    Add-Check 'Notification restart did not enqueue an automatic duplicate email' ($mailCountBeforeNotificationRestart -eq $mailCountAfterNotificationRestart)

    $spoofBody = @{ recipientEmail = $internalRecipient; verificationCode = '001234'; recipient_email = $wrongRecipient; account_id = $accountB; role = 'ADMIN'; account_status = 'ACTIVE'; expires_at = '2099-01-01T00:00:00Z'; failed_attempts = 0; revoked_at = $null }
    $mailCountBeforeAuthProbe = @(Get-MailMessages).Count
    $noToken = Invoke-Http 'POST' ($notificationUrl + '/internal/v1/email-verifications') $spoofBody
    $badToken = Invoke-Http 'POST' ($notificationUrl + '/internal/v1/email-verifications') $spoofBody @{ 'X-StyleMind-Service-Token' = 'wrong-internal-token' }
    $mailCountAfterDenied = @(Get-MailMessages).Count
    Add-Check 'Missing and invalid internal tokens were denied without sending email' ($noToken.Status -eq 401 -and $badToken.Status -eq 401 -and $mailCountAfterDenied -eq $mailCountBeforeAuthProbe)
    $goodToken = Invoke-Http 'POST' ($notificationUrl + '/internal/v1/email-verifications') $spoofBody @{ 'X-StyleMind-Service-Token' = $serviceToken }
    Add-Check 'Authorized internal notification returned 202' ($goodToken.Status -eq 202)
    Add-Check 'Unknown recipient field did not redirect the real Mailpit message' ((Get-MailCount $internalRecipient) -eq 1 -and (Get-MailCount $wrongRecipient) -eq 0 -and @(Get-MailMessages).Count -eq ($mailCountBeforeAuthProbe + 1))

    $identityContainerData = (& docker inspect (Get-ServiceContainerId 'identity-service') 2>$null | ConvertFrom-Json)[0]
    $privateKeyMount = @($identityContainerData.Mounts | Where-Object { $_.Destination -eq '/run/secrets/stylemind-identity-private.pem' })
    Add-Check 'Identity signing key is mounted read-only' ($privateKeyMount.Count -eq 1 -and -not $privateKeyMount[0].RW)
    $postgresId = Get-ServiceContainerId 'postgres'
    $postgresContainerData = (& docker inspect $postgresId 2>$null | ConvertFrom-Json)[0]
    $postgresVolume = @($postgresContainerData.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql' })
    Add-Check 'Disposable Postgres uses a project-scoped named volume' ($postgresVolume.Count -eq 1 -and $postgresVolume[0].Type -eq 'volume' -and $postgresVolume[0].Name.StartsWith($project))
    $volumeName = [string]$postgresVolume[0].Name

    $stage = 'restart-postgresql-and-recover'
    $recoveryStarted = Get-Date
    $null = Invoke-Compose @('restart', 'postgres')
    $deadline = (Get-Date).AddSeconds(150)
    do {
        $pgHealth = Get-ContainerHealth $postgresId
        if ($pgHealth -eq 'healthy') { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    $recoverySeconds = [math]::Round(((Get-Date) - $recoveryStarted).TotalSeconds, 1)
    Add-Check 'Postgres returned healthy on the same container and named volume' ((Get-ContainerHealth $postgresId) -eq 'healthy')
    Wait-HttpHealth ($identityUrl + '/actuator/health') 'identity-service-after-postgres'
    Wait-HttpHealth ($notificationUrl + '/actuator/health') 'notification-service-after-postgres'
    $flywayAfterRestart = Invoke-Psql 'stylemind_identity' "SELECT string_agg(version || ':' || success::text, ',' ORDER BY installed_rank) FROM flyway_schema_history"
    Add-Check 'Postgres recovery did not apply or repair migrations' ($flywayAfterRestart -eq $flywayBeforeRestart)

    $stage = 'reset-password-after-postgres-recovery'
    $resetResult = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/reset-password') @{ reset_token = $resetToken; new_password = $passwordANew }
    Add-Check 'Reset grant and transaction completed after PostgreSQL recovery' ($resetResult.Status -eq 204)
    $activeSessionsAImmediatelyAfterReset = Invoke-Psql 'stylemind_identity' "SELECT count(*) FROM refresh_session WHERE account_id = '$accountA' AND revoked_at IS NULL"
    Add-Check 'Reset revoked all A refresh sessions that existed at commit time' ($activeSessionsAImmediatelyAfterReset -eq '0')
    $oldPasswordLogin = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/login') @{ email = $emailA; password = $passwordA }
    $newPasswordLogin = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/login') @{ email = $emailA; password = $passwordANew }
    Add-Check 'Old password is rejected and new password can login' ($oldPasswordLogin.Status -eq 401 -and $newPasswordLogin.Status -eq 200)
    $resetReplay = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/reset-password') @{ reset_token = $resetToken; new_password = $passwordANew }
    Add-Check 'Reset token is one-use' ($resetReplay.Status -eq 400)
    $revokedRefresh = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/refresh') @{ refresh_token = $refreshA2 }
    Add-Check 'Successful reset revoked A refresh session' ($revokedRefresh.Status -eq 401)
    $refreshBCheck = Invoke-Http 'POST' ($identityUrl + '/api/v1/auth/refresh') @{ refresh_token = $refreshB }
    Add-Check 'B refresh session remains usable after A reset' ($refreshBCheck.Status -eq 200)
    $jwtAfterReset = Invoke-Http 'GET' ($identityUrl + '/api/v1/audit-protected-probe') $null @{ Authorization = "Bearer $accessA" }
    Add-Check 'Existing access JWT remains accepted through restart and password reset' ($jwtAfterReset.Status -eq 404)

    $finalStateA = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text || '|' || (password_hash <> '$passwordANew')::text FROM account WHERE account_id = '$accountA'"
    $finalStateB = Invoke-Psql 'stylemind_identity' "SELECT account_status || '|' || (email_verified_at IS NOT NULL)::text FROM account WHERE account_id = '$accountB'"
    $activeSessionsB = Invoke-Psql 'stylemind_identity' "SELECT count(*) FROM refresh_session WHERE account_id = '$accountB' AND revoked_at IS NULL"
    $consumedResetCount = Invoke-Psql 'stylemind_identity' "SELECT count(*) FROM password_reset_challenge WHERE account_id = '$accountA' AND consumed_at IS NOT NULL"
    Add-Check 'Final database confirms A active with a non-plaintext password and B unchanged/active' ($finalStateA -eq 'ACTIVE|true|true' -and $finalStateB -eq 'ACTIVE|true')
    Add-Check 'Final database confirms reset challenge consumed and B refresh session remains active' ($consumedResetCount -eq '1' -and [int]$activeSessionsB -ge 1)

    $checks.Add("PostgreSQL restart-to-healthy time: $recoverySeconds seconds (observed; no SLA inferred)")
    $checks.Add("Disposable Flyway history before/after: $flywayBeforeRestart")
    $checks.Add("Disposable data volume: $volumeName")
    $checks.Add('OTP, access/refresh tokens, reset token and password values are omitted from evidence')
} catch {
    if ($_.Exception.Message -like 'AUDIT_*') {
        $failureType = $_.Exception.Message
    } else {
        $failureType = $_.Exception.GetType().Name
    }
} finally {
    if ($stackStarted) {
        $projectContainers = @(& docker ps -aq --filter "label=com.docker.compose.project=$project")
        $projectVolumes = @(& docker volume ls --filter "label=com.docker.compose.project=$project" --format '{{.Name}}')
        $foreignContainer = $false
        foreach ($containerId in $projectContainers) {
            $info = (& docker inspect $containerId 2>$null | ConvertFrom-Json)[0]
            if ($info.Config.Labels.'com.docker.compose.project' -ne $project) { $foreignContainer = $true }
        }
        $foreignVolume = @($projectVolumes | Where-Object { $_ -notlike ($project + '_*') }).Count -gt 0
        if (-not $foreignContainer -and -not $foreignVolume) {
            try {
                $null = Invoke-Compose @('down', '-v', '--remove-orphans')
                $remainingContainers = @(& docker ps -aq --filter "label=com.docker.compose.project=$project")
                $remainingVolumes = @(& docker volume ls --filter "label=com.docker.compose.project=$project" --format '{{.Name}}')
                if ($remainingContainers.Count -eq 0 -and $remainingVolumes.Count -eq 0) { $cleanupStatus = 'exact disposable Compose project and volume removed' }
                else { $cleanupStatus = 'cleanup incomplete; matching project containers or volumes remain' }
            } catch { $cleanupStatus = 'cleanup command failed for the exact disposable Compose project' }
        } else {
            $cleanupStatus = 'cleanup withheld because project labels or volume names did not match the unique project'
        }
    }
    $finishedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss.fffK')
    $resultLines = @(
        '# Disposable restart and recovery audit',
        '',
        "- Compose project: $project",
        "- Started: $startedAt",
        "- Finished: $finishedAt",
        "- Result stage: $stage",
        "- Result: $(if ($failureType) { 'INCOMPLETE' } else { 'PASS' })",
        "- Failure type: $(if ($failureType) { $failureType } else { 'none' })",
        "- Cleanup: $cleanupStatus",
        "- PostgreSQL recovery: $(if ($null -ne $recoverySeconds) { "$recoverySeconds seconds" } else { 'not reached' })",
        '',
        '## Checks',
        ''
    )
    $resultLines += $checks | ForEach-Object { "- PASS: $_" }
    if ($observations.Count) {
        $resultLines += ''
        $resultLines += '## Contract gaps with observed behavior'
        $resultLines += ''
        $resultLines += $observations | ForEach-Object { "- $_" }
    }
    $resultLines | Set-Content -LiteralPath $reportPath -Encoding utf8
    "DISPOSABLE_PROJECT=$project RESULT=$(if ($failureType) { 'INCOMPLETE' } else { 'PASS' }) STAGE=$stage CHECKS=$($checks.Count) CLEANUP=$cleanupStatus"
    if ($failureType) { "FAILURE_TYPE=$failureType" }
}

if ($failureType) { exit 1 }
