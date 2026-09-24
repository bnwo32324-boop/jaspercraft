# JasperCraft "Undo last deploy". Started ONLY by the owner double-clicking "Undo last deploy.bat".
# Puts back the newest backup made by a successful deploy, the same safe way the deployer works:
# old jars are staged in plugins\update\ (never overwritten while the server runs), website files are
# copied back, the server restarts only when plugins change and only when nobody is online, and the
# result is checked. If the undo itself does not start cleanly, the deployed version is put back.
#
# Windows PowerShell 5.1, pure ASCII. Never creates .runtime\maintenance-mode.
# Exit codes: 100 done / nothing to undo, 101 stopped or cancelled (nothing changed), 102 failed.
param(
    [string]$GameRoot = 'C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale',
    [string]$StagingRoot = 'C:\Users\AM\Documents\jaspercraft-deploy',
    [string]$JdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot',
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DeployLogic.ps1')
. (Join-Path $PSScriptRoot 'DeployRuntime.ps1')

$script:ExitCode = 101

function Stop-Undo {
    param([string]$Why, [string]$Level = 'Warn')
    Say $Why $Level
    throw 'DEPLOY_STOPPED'
}

function Find-NewestDeployBackup {
    $dirs = @(Get-ChildItem -LiteralPath $script:Ctx.DeployDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^backup-\d{8}-\d{6}$' } | Sort-Object Name -Descending)
    foreach ($d in $dirs) {
        $m = Read-JsonFile (Join-Path $d.FullName 'manifest.json')
        if ($null -ne $m -and $m.status -eq 'deployed') { return $d.FullName }
    }
    return $null
}

function Invoke-Undo {
    $ctx = $script:Ctx
    Say 'JasperCraft - undo last deploy' 'Step'
    $backup = Find-NewestDeployBackup
    if ($null -eq $backup) {
        Say 'There is no deploy to undo.' 'Good'
        $script:ExitCode = 100
        return
    }
    $m = Read-JsonFile (Join-Path $backup 'manifest.json')
    $entries = @($m.entries)
    $plugins = @($entries | Where-Object { $_.kind -eq 'Plugin' } | ForEach-Object { Get-PluginNameFromPath $_.path })
    $site = @($entries | Where-Object { $_.kind -eq 'Site' })
    $other = @($entries | Where-Object { $_.kind -eq 'Other' })
    $when = $m.created
    try { $when = ([datetime]::Parse($m.created, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind)).ToLocalTime().ToString('dddd d MMMM yyyy, HH:mm') } catch {}

    # Undo only the deploy that is live right now.
    $gameHead = (Invoke-Git -Repo $ctx.Game -Arguments @('rev-parse', 'HEAD')).Out.Trim()
    if ($m.toCommit -and $gameHead -ne $m.toCommit) {
        Stop-Undo 'The live folder changed since the last deploy (a newer update or a manual change), so this backup no longer matches. Nothing was changed. Please ask Claude.'
    }
    $modified = Get-GameModifiedPaths
    $clash = @($entries | Where-Object { $_.kind -ne 'Plugin' -and ($modified -contains $_.path) } | ForEach-Object { $_.path })
    if ($clash.Count -gt 0) {
        Say 'These files were edited on this PC after the deploy:' 'Warn'
        foreach ($c in $clash) { Say ('    ' + $c) }
        Stop-Undo 'To be safe, nothing was changed. Please ask Claude.'
    }

    Say ('This puts back what was live before the deploy of ' + $when + '.') 'Step'
    if ($plugins.Count -gt 0) { Say ('Plugins: ' + ($plugins -join ', ') + '  (needs a server restart, about 1-3 minutes)') }
    if ($site.Count -gt 0) { Say ('Website: ' + $site.Count + ' file(s)') }
    if ($other.Count -gt 0) { Say ('Other files (folder copy only): ' + $other.Count) }
    if (-not (Read-YesNo 'Undo the last deploy now?')) { Say 'Cancelled. Nothing was changed.' 'Warn'; return }

    if ($plugins.Count -gt 0) {
        Say 'Checking that nobody is playing right now...' 'Step'
        if (-not (Wait-RestartWindow)) { Stop-Undo 'Cancelled while waiting. Nothing was changed.' }
        Say 'Nobody is online. Going ahead.' 'Good'
    }

    $ops = Get-RestoreOperations $backup
    $res = Invoke-LiveChange -Operations $ops -FromCommit $m.toCommit -ToCommit $m.fromCommit -BackupPrefix 'undo'
    $summary = @()
    if ($plugins.Count -gt 0) { $summary += ('plugins ' + ($plugins -join ', ')) }
    if ($site.Count -gt 0) { $summary += ('' + $site.Count + ' website file(s)') }
    if ($other.Count -gt 0) { $summary += ('' + $other.Count + ' other file(s)') }

    if (-not $res.Ok) {
        if ($res.RollbackOk) {
            Publish-DeployStatus -Result 'UNDO FAILED - deployed version kept' -Commit $m.toCommit -PreviousCommit $m.fromCommit -Summary ($summary -join '; ') -Notes @('Undo failed: ' + $res.Reason) -LogText $res.LogText
            Say ('The undo did not work: ' + $res.Reason) 'Bad'
            Say 'The version from the last deploy was put back and is running. Please tell Claude.' 'Warn'
        } else {
            Publish-DeployStatus -Result 'UNDO FAILED - and putting the deployed version back also failed' -Commit $m.toCommit -PreviousCommit $m.fromCommit -Summary ($summary -join '; ') -Notes @('Undo failed: ' + $res.Reason, 'Restore failed: ' + $res.RollbackReason) -LogText $res.LogText
            Say ('The undo did not work: ' + $res.Reason) 'Bad'
            Say ('Putting things back did not start cleanly either: ' + $res.RollbackReason) 'Bad'
            Say ('Please ask Claude for help. Backups are in ' + $ctx.DeployDir) 'Bad'
        }
        $script:ExitCode = 102
        return
    }

    Set-BackupStatus $backup 'undone'
    Set-BackupStatus $res.BackupDir 'undo-applied'
    Save-DeployState @{ commit = $m.fromCommit; previousCommit = $m.toCommit; deployedAt = (Get-Date).ToUniversalTime().ToString('o'); lastResult = 'undone'; backup = $res.BackupDir }
    try {
        if ($m.fromCommit) {
            [void](Invoke-Git -Repo $ctx.Game -Arguments @('update-ref', 'refs/deploy/live', $m.fromCommit))
            [void](Invoke-Git -Repo $ctx.Game -Arguments @('reset', '--mixed', '-q', $m.fromCommit))
        }
    } catch {
        Write-DeployLog ('Git reset of the live folder failed: ' + $_.Exception.Message)
        Say 'The old version is live, but updating the folder''s git copy had a problem (the game is fine). Please mention it to Claude.' 'Warn'
    }
    if (Test-Path -LiteralPath (Join-Path $ctx.Staging '.git')) {
        Publish-DeployStatus -Result 'UNDONE - previous version restored by the owner' -Commit $m.fromCommit -PreviousCommit $m.toCommit -Summary ($summary -join '; ') -Notes @('The owner pressed Undo; the deploy of ' + $m.toCommit + ' was taken off the live server.') -LogText $res.LogText
    }
    Say 'Done. The previous version is live again.' 'Good'
    $script:ExitCode = 100
}

$ctxReady = $false
try {
    [void](Initialize-DeployContext -GameRoot $GameRoot -StagingRoot $StagingRoot -JdkRoot $JdkRoot)
    $ctxReady = $true
    if (-not (Enter-DeployLock)) {
        Say 'The deployer (or Undo) is already running in another window. Close that one first.' 'Warn'
    } else {
        Write-DeployLog '==== Undo started'
        Invoke-Undo
    }
} catch {
    if ($_.Exception.Message -ne 'DEPLOY_STOPPED') {
        if ($ctxReady) { Write-DeployLog ('Unexpected error: ' + $_.Exception.Message + "`n" + $_.ScriptStackTrace) }
        Write-Host ('  Something unexpected went wrong: ' + $_.Exception.Message) -ForegroundColor Red
        Write-Host '  Please tell Claude.' -ForegroundColor Red
        $script:ExitCode = 102
    }
} finally {
    if ($ctxReady) { Write-DeployLog ('==== Undo finished, exit code ' + $script:ExitCode) }
    Exit-DeployLock
    if ($ctxReady) { Write-Host ('  Full log: ' + $script:Ctx.LogFile) -ForegroundColor DarkGray }
    if (-not $NoPause) { Wait-AnyKey }
}
exit $script:ExitCode
