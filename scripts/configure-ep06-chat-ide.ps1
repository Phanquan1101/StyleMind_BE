[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$imageRepo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'ep06-chat-runtime/Read-ChatImageEnvironment.ps1')
$imageValues=Read-ChatImageEnvironment -Path (Join-Path $imageRepo '.env')
$imageDestination=Join-Path $imageRepo '.env.chat.local'
if(Test-Path -LiteralPath $imageDestination) {
    throw '.env.chat.local already exists. Update its six image variables locally; this script does not overwrite it.'
}
$imageText="# Generated from the six Chat image variables only. Local secret file; never commit.`n"
foreach($imageName in @('STYLEMIND_CHAT_IMAGES_ENABLED','STYLEMIND_CHAT_IMAGES_BUCKET',
        'STYLEMIND_CHAT_IMAGES_ENDPOINT','STYLEMIND_CHAT_IMAGES_REGION','AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY')) {
    # B2 credentials and endpoint do not require dotenv escaping. Reject ambiguous content.
    if($imageValues[$imageName] -notmatch '^[A-Za-z0-9_:/+.=\-]+$') {
        throw "Unsupported character in image variable: $imageName"
    }
    $imageText+=$imageName+'='+$imageValues[$imageName]+"`n"
}
[IO.File]::WriteAllText($imageDestination,$imageText,[Text.UTF8Encoding]::new($false))
Write-Output 'Created ignored .env.chat.local containing only six image variables. No values printed.'
Write-Output 'VS Code: set envFile to ${workspaceFolder}/.env.chat.local in the Wardrobe Java launch configuration.'
Write-Output 'Keep existing database/JWT environment configuration. No application was started.'
