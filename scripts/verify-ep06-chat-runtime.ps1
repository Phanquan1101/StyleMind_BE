[CmdletBinding()]
param([switch]$SkipRegression,[switch]$LiveBackblaze)

$ErrorActionPreference = 'Stop'
$runtimeRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeMode=if($LiveBackblaze){'backblaze'}else{'fake'}
$runtimeEvidence = Join-Path $runtimeRepo ('wardrobe-community-service/target/ep06-chat-runtime-'+$runtimeMode+'-'+(Get-Date -Format yyyyMMddHHmmss))
New-Item -ItemType Directory -Path $runtimeEvidence -Force | Out-Null
$runtimeSkip = $SkipRegression.IsPresent.ToString().ToLowerInvariant()
$runtimeScript = @'
set -u
mkdir -p /tmp/ep06-runtime-apps
for service in notification-service identity-service wardrobe-community-service api-gateway; do
  mkdir -p "/tmp/ep06-runtime-apps/$service"
  cp "/repo/$service/pom.xml" "/tmp/ep06-runtime-apps/$service/pom.xml"
  cp -R "/repo/$service/src" "/tmp/ep06-runtime-apps/$service/src"
  cd "/tmp/ep06-runtime-apps/$service"
  if [ "$service" = "wardrobe-community-service" ] && [ "$EP06_SKIP_REGRESSION" != "true" ]; then
    env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY STYLEMIND_CHAT_IMAGES_ENABLED=false mvn -B -ntp verify > "/evidence/$service-package.log" 2>&1
  else
    mvn -B -ntp -DskipTests package > "/evidence/$service-package.log" 2>&1
  fi
  result=$?
  grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' "/evidence/$service-package.log"
  if [ "$result" -ne 0 ]; then exit "$result"; fi
  cp "target/$service-0.0.1-SNAPSHOT.jar" "/tmp/ep06-runtime-apps/$service.jar"
done
mkdir -p /tmp/ep06-runtime-harness
cp /repo/scripts/ep06-chat-runtime/pom.xml /tmp/ep06-runtime-harness/pom.xml
cp -R /repo/scripts/ep06-chat-runtime/src /tmp/ep06-runtime-harness/src
cd /tmp/ep06-runtime-harness
mvn -B -ntp test -Dep06.repo=/repo -Dep06.evidence=/evidence -Dep06.notificationJar=/tmp/ep06-runtime-apps/notification-service.jar -Dep06.identityJar=/tmp/ep06-runtime-apps/identity-service.jar -Dep06.wardrobeJar=/tmp/ep06-runtime-apps/wardrobe-community-service.jar -Dep06.gatewayJar=/tmp/ep06-runtime-apps/api-gateway.jar > /evidence/runtime-maven.log 2>&1
result=$?
if [ -d target/surefire-reports ]; then mkdir -p /evidence/runtime-surefire-reports; cp -R target/surefire-reports/. /evidence/runtime-surefire-reports/; fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\] (COMPILATION ERROR|Failed to execute goal)|^EP06_(RUNTIME_|BACKBLAZE_)' /evidence/runtime-maven.log
for summary in /evidence/newman-*-summary.txt; do
  if [ -f "$summary" ]; then cat "$summary"; fi
done
exit "$result"
'@
$runtimeRunnerPath=Join-Path $runtimeEvidence 'run-runtime.sh'
[IO.File]::WriteAllText($runtimeRunnerPath,$runtimeScript.Replace("`r`n","`n"),[Text.UTF8Encoding]::new($false))
$runtimeRunnerName='stylemind-ep06-chat-runtime-'+(Get-Date -Format yyyyMMddHHmmss)
$runtimeArguments=@('run','--rm','--name',$runtimeRunnerName,'--mount',"type=bind,source=$runtimeRepo,target=/repo,readonly",
    '--mount',"type=bind,source=$runtimeEvidence,target=/evidence",'--mount','type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
    '--mount','type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2',
    '-e','TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal','-e',"EP06_TEST_RUNNER_CONTAINER=$runtimeRunnerName",'-e',"EP06_SKIP_REGRESSION=$runtimeSkip")
$runtimePrevious=@{}
try {
    if($LiveBackblaze) {
        . (Join-Path $PSScriptRoot 'ep06-chat-runtime/Read-ChatImageEnvironment.ps1')
        $runtimeImages=Read-ChatImageEnvironment -Path (Join-Path $runtimeRepo '.env')
        if($runtimeImages.STYLEMIND_CHAT_IMAGES_BUCKET -cne 'stylemind-chat' -or
                $runtimeImages.STYLEMIND_CHAT_IMAGES_ENDPOINT -cne 'https://s3.us-east-005.backblazeb2.com' -or
                $runtimeImages.STYLEMIND_CHAT_IMAGES_REGION -cne 'us-east-005') {
            throw 'Live verification is restricted to the owner-selected Backblaze bucket/endpoint/region.'
        }
        foreach($runtimeImageName in $runtimeImages.Keys) {
            $runtimePrevious[$runtimeImageName]=[Environment]::GetEnvironmentVariable($runtimeImageName,'Process')
            [Environment]::SetEnvironmentVariable($runtimeImageName,$runtimeImages[$runtimeImageName],'Process')
            $runtimeArguments+=@('-e',$runtimeImageName)
        }
        $runtimeArguments+=@('-e','EP06_LIVE_BACKBLAZE=true')
        Write-Output 'Live Backblaze enabled: only test-created images are written and cleaned up; credentials omitted.'
    }
    Write-Output ('Evidence: '+$runtimeEvidence)
    $runtimeArguments+=@('maven:3.9.11-eclipse-temurin-21-alpine','sh','/evidence/run-runtime.sh')
    & docker @runtimeArguments
    $runtimeResult=$LASTEXITCODE
} finally {
    foreach($runtimeImageName in $runtimePrevious.Keys) {
        [Environment]::SetEnvironmentVariable($runtimeImageName,$runtimePrevious[$runtimeImageName],'Process')
    }
}
exit $runtimeResult
