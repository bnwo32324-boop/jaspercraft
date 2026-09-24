param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$siteRoot = Join-Path $projectRoot 'site'
foreach ($required in @('index.html','client.html','jaspr-sso.js','jaspr-profile.js','jaspr-client.js','jaspr-sso.css','jaspercraft-banner.png','jaspercraft-cat-face.png','classes.js','classes.js.map','assets.epk','favicon.png','lang\en_us.lang')) {
    $path = Join-Path $siteRoot $required
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing required client file: $path" }
}
rg -a -q 'eaglercraft\.minecraft.*1\.12\.2' (Join-Path $siteRoot 'classes.js')
if ($LASTEXITCODE -ne 0) { throw 'classes.js does not identify itself as Minecraft 1.12.2.' }
rg -q '/jaspercraft/socket' (Join-Path $siteRoot 'jaspr-client.js')
if ($LASTEXITCODE -ne 0) { throw 'The same-origin multiplayer endpoint is missing.' }
foreach ($script in @('jaspr-sso.js','jaspr-profile.js','jaspr-client.js')) {
    & node --check (Join-Path $siteRoot $script)
    if ($LASTEXITCODE -ne 0) { throw "Invalid JavaScript: $script" }
}
Write-Output 'Verified the precompiled Eaglercraft 1.12.2 web distribution.'
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $siteRoot 'classes.js'),(Join-Path $siteRoot 'assets.epk')
