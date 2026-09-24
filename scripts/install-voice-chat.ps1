param([switch]$SkipGameRestart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$serverRoot = Join-Path $projectRoot 'server'
$pluginsRoot = Join-Path $serverRoot 'plugins'
$candidateRoot = Join-Path $projectRoot 'candidate\voice-chat'
$runtimeRoot = Join-Path $projectRoot '.runtime'

$jars = @('JasprVoiceChat.jar', 'TestServerControl.jar')
foreach ($name in $jars) {
    $candidate = Join-Path $candidateRoot $name
    if (-not (Test-Path -LiteralPath $candidate)) { throw "Missing candidate jar: $candidate. Run scripts\build-voice-plugin.ps1 first." }
}

$backupRoot = Join-Path $runtimeRoot ('plugin-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-voice')
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
foreach ($name in $jars) {
    $installed = Join-Path $pluginsRoot $name
    if (Test-Path -LiteralPath $installed) { Copy-Item -LiteralPath $installed -Destination (Join-Path $backupRoot $name) }
}
Write-Output "Backed up the replaced plugins to $backupRoot"

foreach ($name in $jars) {
    Copy-Item -LiteralPath (Join-Path $candidateRoot $name) -Destination (Join-Path $pluginsRoot $name) -Force
    Write-Output ("Installed " + $name)
}

if ($SkipGameRestart) {
    Write-Output 'Skipping the Paper restart. The plugins load on the next restart.'
} else {
    & (Join-Path $PSScriptRoot 'stop-game-server.ps1')
    & (Join-Path $PSScriptRoot 'start-game-server.ps1')
}

Write-Output ''
Write-Output 'Paper now carries the voice relay on loopback port 24454.'
Write-Output 'One step is left, in the Jaspr.chat project, so the browser can reach it:'
Write-Output '  cd "C:\Users\AM\Desktop\Curser Test\Jaspergers"'
Write-Output '  powershell -ExecutionPolicy Bypass -File scripts\install-host-supervisor.ps1 -Restart'
Write-Output 'Then reload https://jaspr.chat/jaspercraft/ and allow the microphone.'
