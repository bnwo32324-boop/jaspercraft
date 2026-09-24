param([switch]$SkipGameRestart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pluginsRoot = Join-Path $projectRoot 'server\plugins'
$candidate = Join-Path $projectRoot 'candidate\invasions\JasprInvasions.jar'
$runtimeRoot = Join-Path $projectRoot '.runtime'

if (-not (Test-Path -LiteralPath $candidate)) { throw "Missing candidate jar: $candidate. Run scripts\build-invasions-plugin.ps1 first." }

$installed = Join-Path $pluginsRoot 'JasprInvasions.jar'
if (Test-Path -LiteralPath $installed) {
    $backupRoot = Join-Path $runtimeRoot ('plugin-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-invasions')
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $installed -Destination (Join-Path $backupRoot 'JasprInvasions.jar')
    Write-Output "Backed up the previous plugin to $backupRoot"
}

if ($SkipGameRestart) {
    # Paper holds the jar open while it is running, so a live replacement goes through Bukkit's
    # update folder instead and is swapped in on the next restart.
    $updateRoot = Join-Path $pluginsRoot 'update'
    New-Item -ItemType Directory -Force -Path $updateRoot | Out-Null
    Copy-Item -LiteralPath $candidate -Destination (Join-Path $updateRoot 'JasprInvasions.jar') -Force
    Write-Output 'Staged JasprInvasions.jar in the update folder. It loads on the next restart.'
    return
}

# Stop first, so the running JVM is not holding the jar we are about to overwrite.
& (Join-Path $PSScriptRoot 'stop-game-server.ps1')
Copy-Item -LiteralPath $candidate -Destination $installed -Force
Write-Output 'Installed JasprInvasions.jar'
& (Join-Path $PSScriptRoot 'start-game-server.ps1')
Write-Output ''
Write-Output 'Invasions are live. An operator can test immediately with /invasion.'
