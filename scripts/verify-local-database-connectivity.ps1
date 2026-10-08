param(
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$projectName = 'stylemind-task4'
$composePrefix = @('compose', '--project-name', $projectName)

function Invoke-Compose {
    param([string[]]$Arguments)

    $allArguments = $composePrefix + $Arguments
    $output = & docker @allArguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($Arguments -join ' ')"
    }
    return $output
}

$expectedServices = @(
    @{ Name = 'identity-service'; Port = 8081 },
    @{ Name = 'commerce-service'; Port = 8082 },
    @{ Name = 'payment-service'; Port = 8083 },
    @{ Name = 'wardrobe-community-service'; Port = 8084 },
    @{ Name = 'stylist-service'; Port = 8085 },
    @{ Name = 'notification-service'; Port = 8086 }
)

$runningServices = Invoke-Compose -Arguments @('ps', '--services', '--filter', 'status=running')
foreach ($service in @('postgres') + @($expectedServices | ForEach-Object { $_.Name })) {
    if ($runningServices -notcontains $service) {
        throw "Required Compose service is not running: $service"
    }
}

foreach ($service in $expectedServices) {
    $uri = "http://localhost:$($service.Port)/actuator/health"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $healthy = $false

    do {
        try {
            $response = Invoke-WebRequest -Uri $uri -TimeoutSec 5 -UseBasicParsing
            if ([int]$response.StatusCode -eq 200) {
                $content = $response.Content
                if ($content -is [byte[]]) {
                    $content = [System.Text.Encoding]::UTF8.GetString($content)
                }
                $health = $content | ConvertFrom-Json
                if ($health.status -eq 'UP' -and $health.components.db.status -eq 'UP') {
                    $healthy = $true
                    break
                }
            }
        } catch {
            # The services may still be starting or waiting for PostgreSQL.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if (-not $healthy) {
        throw "Health check did not report HTTP 200, overall UP, and db UP for $($service.Name) at $uri"
    }

    Write-Output "$($service.Name): HTTP 200, overall UP, db UP"
}

$boundaryOutput = Invoke-Compose -Arguments @(
    'exec', '--no-TTY', 'postgres', 'sh', '-ec',
    'psql --username "$POSTGRES_USER" --dbname postgres --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align --file /opt/stylemind/verify-boundaries.sql'
)
if (($boundaryOutput -join "`n") -notmatch 'PERSISTENCE_BOUNDARIES_OK') {
    throw 'Database ownership and cross-service connection boundary verification failed'
}

Write-Output 'Database ownership and cross-service connection boundaries: PASS'
