param([switch]$SkipGameRestart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pluginsRoot = Join-Path $projectRoot 'server\plugins'
$candidate = Join-Path $projectRoot 'candidate\disasters\JasprDisasters.jar'
$runtimeRoot = Join-Path $projectRoot '.runtime'

if (-not (Test-Path -LiteralPath $candidate)) { throw "Missing candidate jar: $candidate. Run scripts\build-disasters-plugin.ps1 first." }

$installed = Join-Path $pluginsRoot 'JasprDisasters.jar'
if (Test-Path -LiteralPath $installed) {
    $backupRoot = Join-Path $runtimeRoot ('plugin-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-disasters')
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $installed -Destination (Join-Path $backupRoot 'JasprDisasters.jar')
    Write-Output "Backed up the previous plugin to $backupRoot"
}

Copy-Item -LiteralPath $candidate -Destination $installed -Force
Write-Output 'Installed JasprDisasters.jar'

if ($SkipGameRestart) {
    Write-Output 'Skipping the Paper restart. The plugin loads on the next restart.'
} else {
    & (Join-Path $PSScriptRoot 'stop-game-server.ps1')
    & (Join-Path $PSScriptRoot 'start-game-server.ps1')
    Write-Output ''
    Write-Output 'Meteor showers are live. An operator can test immediately with /shower.'
}
