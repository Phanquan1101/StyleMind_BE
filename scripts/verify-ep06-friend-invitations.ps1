[CmdletBinding()]
param(
    [ValidateSet('Focused','Regression')][string]$Mode='Focused',
    [ValidateSet('notification-service','identity-service','wardrobe-community-service')][string[]]$Services=@()
)
$ErrorActionPreference='Stop'
$invitationRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$invitationEvidence=Join-Path $invitationRepo ("wardrobe-community-service/target/ep06-invitations-20261028/"+$Mode)
New-Item -ItemType Directory -Force -Path $invitationEvidence | Out-Null
$invitationScript=@'
set -u
for service in notification-service identity-service wardrobe-community-service; do
  mkdir -p "/tmp/ep06-invitations/$service"
  cp "/repo/$service/pom.xml" "/tmp/ep06-invitations/$service/pom.xml"
  cp -R "/repo/$service/src" "/tmp/ep06-invitations/$service/src"
done
overall=0
if [ "$EP06_MODE" = "Regression" ]; then
  services="notification-service identity-service wardrobe-community-service"
else
  services="identity-service wardrobe-community-service"
fi
if [ -n "$EP06_SERVICES" ]; then services="$EP06_SERVICES"; fi
for service in $services; do
  cd "/tmp/ep06-invitations/$service"
  if [ "$EP06_MODE" = "Focused" ]; then
    if [ "$service" = "identity-service" ]; then
      cases="InternalAccountLookupPostgresIntegrationTests"
    else
      cases="FriendInvitationsApiPostgresIntegrationTests,FriendInvitationStateMigrationPostgresTests,FriendInvitationRequestValidatorTests,FriendInvitationServiceTests,IdentityEmailVerificationClientTests,FriendshipServiceTests"
    fi
    mvn -B -ntp "-Dtest=$cases" test > "/evidence/$service.log" 2>&1
  else
    mvn -B -ntp verify > "/evidence/$service.log" 2>&1
  fi
  result=$?
  if [ -d target/surefire-reports ]; then
    mkdir -p "/evidence/$service-reports"
    cp -R target/surefire-reports/. "/evidence/$service-reports/"
  fi
  grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\] (COMPILATION ERROR|Failed to execute goal)' "/evidence/$service.log" || true
  if [ "$result" -ne 0 ]; then overall=1; fi
done
exit "$overall"
'@
$invitationName='stylemind-ep06-invitations-'+(Get-Date -Format yyyyMMddHHmmss)
$invitationRunnerPath=Join-Path $invitationEvidence 'run-tests.sh'
[IO.File]::WriteAllText($invitationRunnerPath,$invitationScript.Replace("`r`n","`n"),[Text.UTF8Encoding]::new($false))
docker run --rm --name $invitationName --mount "type=bind,source=$invitationRepo,target=/repo,readonly" --mount "type=bind,source=$invitationEvidence,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e "EP06_MODE=$Mode" -e "EP06_SERVICES=$($Services -join ' ')" maven:3.9.11-eclipse-temurin-21-alpine sh /evidence/run-tests.sh
exit $LASTEXITCODE
