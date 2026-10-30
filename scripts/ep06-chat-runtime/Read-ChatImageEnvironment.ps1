# Dot-source this helper. It reads only the six image variables, never evaluates .env code.
function Read-ChatImageEnvironment {
    param([Parameter(Mandatory=$true)][string]$Path)
    $imageNames=@('STYLEMIND_CHAT_IMAGES_ENABLED','STYLEMIND_CHAT_IMAGES_BUCKET',
        'STYLEMIND_CHAT_IMAGES_ENDPOINT','STYLEMIND_CHAT_IMAGES_REGION',
        'AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY')
    $imageValues=@{}
    foreach($imageLine in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($Path))) {
        if($imageLine -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $imageName=$Matches[1]
            if($imageNames -notcontains $imageName) { continue }
            if($imageValues.ContainsKey($imageName)) { throw "Duplicate image variable: $imageName" }
            $imageValue=$Matches[2].Trim()
            if($imageValue.StartsWith('"') -or $imageValue.StartsWith("'")) {
                $imageQuote=$imageValue.Substring(0,1)
                $imageClose=$imageValue.IndexOf($imageQuote,1)
                if($imageClose -lt 1 -or $imageValue.Substring($imageClose+1).Trim() -notmatch '^(#.*)?$') {
                    throw "Invalid quoted image variable: $imageName"
                }
                $imageValue=$imageValue.Substring(1,$imageClose-1)
            } else { $imageValue=($imageValue -replace '\s+#.*$','').Trim() }
            if([string]::IsNullOrWhiteSpace($imageValue) -or $imageValue -match '[\r\n\x00]' -or
                    $imageValue -match '^(DIEN_|replace-with|replace-me|<)') {
                throw "Missing or placeholder image variable: $imageName"
            }
            $imageValues[$imageName]=$imageValue
        }
    }
    foreach($imageName in $imageNames) {
        if(!$imageValues.ContainsKey($imageName)) { throw "Missing image variable: $imageName" }
    }
    if($imageValues.STYLEMIND_CHAT_IMAGES_ENABLED -cne 'true') { throw 'Chat images must be explicitly enabled.' }
    return ,$imageValues
}
