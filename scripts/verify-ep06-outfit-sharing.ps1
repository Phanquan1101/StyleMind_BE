[CmdletBinding()]
param([ValidateSet('Focused','Regression')][string]$Mode='Focused')
$ErrorActionPreference='Stop'
$outfitRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$outfitEvidence=Join-Path $outfitRepo ('wardrobe-community-service/target/ep06-outfit-sharing-'+$Mode.ToLower()+'-'+(Get-Date -Format yyyyMMddHHmmss))
New-Item -ItemType Directory -Force -Path $outfitEvidence | Out-Null
$outfitScript=@'
set -u
mkdir -p /tmp/ep06-outfits
cp /repo/wardrobe-community-service/pom.xml /tmp/ep06-outfits/pom.xml
cp -R /repo/wardrobe-community-service/src /tmp/ep06-outfits/src
cd /tmp/ep06-outfits
if [ "$EP06_MODE" = "Focused" ]; then
  mvn -B -ntp -Dtest=OutfitVisibilityRequestValidatorTests,OutfitVisibilityMigrationPostgresTests,OutfitSharePostgresIntegrationTests,OutfitPostgresIntegrationTests,FriendInvitationStateMigrationPostgresTests,FriendsPersistencePostgresTests test > /evidence/maven.log 2>&1
else
  mvn -B -ntp verify > /evidence/maven.log 2>&1
fi
result=$?
if [ -d target/surefire-reports ]; then cp -R target/surefire-reports /evidence/; fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\] (COMPILATION ERROR|Failed to execute goal)' /evidence/maven.log || true
exit "$result"
'@
$outfitRunner=Join-Path $outfitEvidence 'run-tests.sh'
[IO.File]::WriteAllText($outfitRunner,$outfitScript.Replace("`r`n","`n"),[Text.UTF8Encoding]::new($false))
Write-Output ('Evidence: '+$outfitEvidence)
docker run --rm --name ('stylemind-ep06-outfits-'+(Get-Date -Format yyyyMMddHHmmss)) --mount "type=bind,source=$outfitRepo,target=/repo,readonly" --mount "type=bind,source=$outfitEvidence,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e "EP06_MODE=$Mode" maven:3.9.11-eclipse-temurin-21-alpine sh /evidence/run-tests.sh
exit $LASTEXITCODE
