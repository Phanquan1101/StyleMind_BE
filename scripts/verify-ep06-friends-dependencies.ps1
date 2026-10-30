[CmdletBinding()]
param([ValidateSet('notification-service','identity-service','wardrobe-community-service')][string[]]$Services=@('notification-service','identity-service','wardrobe-community-service'))
$ErrorActionPreference='Stop'
$dependencyRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dependencyEvidence=Join-Path $dependencyRepo 'wardrobe-community-service/target/ep06-friends-dependencies-20261028'
New-Item -ItemType Directory -Force -Path $dependencyEvidence | Out-Null
$dependencyScript=@'
set -u
for service in notification-service identity-service wardrobe-community-service; do
  mkdir -p "/tmp/ep06-dependencies/$service"
  cp "/repo/$service/pom.xml" "/tmp/ep06-dependencies/$service/pom.xml"
  cp -R "/repo/$service/src" "/tmp/ep06-dependencies/$service/src"
done
overall=0
for service in $EP06_SERVICES; do
  cd "/tmp/ep06-dependencies/$service"
  mvn -B -ntp verify > "/evidence/$service-verify.log" 2>&1
  result=$?
  if [ -d target/surefire-reports ]; then
    mkdir -p "/evidence/$service-surefire-reports"
    cp -R target/surefire-reports/. "/evidence/$service-surefire-reports/"
  fi
  grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' "/evidence/$service-verify.log"
  if [ "$result" -ne 0 ]; then overall=1; fi
done
exit "$overall"
'@
docker run --rm --name stylemind-ep06-dependencies-20261028 --mount "type=bind,source=$dependencyRepo,target=/repo,readonly" --mount "type=bind,source=$dependencyEvidence,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e "EP06_SERVICES=$($Services -join ' ')" maven:3.9.11-eclipse-temurin-21-alpine sh -c $dependencyScript
exit $LASTEXITCODE
