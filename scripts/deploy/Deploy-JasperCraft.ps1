# JasperCraft one-click deployer. Started ONLY by the owner double-clicking "Deploy JasperCraft.bat"
# (no scheduled task, no service, no auto-start). Puts what was merged on GitHub (origin/main) live:
# changed plugin jars (server/plugins/Jaspr*.jar, TestServerControl.jar) and website files (site/**).
#
#   1. fetch origin/main in a separate staging clone (the live folder's files are never touched by git)
#   2. show the new commits and ask Y/N
#   3. Gate 1: throwaway test server on port 25597 with the new jars
#   4. Gate 2: wait until nobody is online and the server has run 5+ minutes (never kicks anyone)
#   5. back up, stage jars in plugins\update\, copy site files, restart, verify; restore on failure
#   6. record the commit, bring the live folder's git in line, push DEPLOY_STATUS.md (deploy-status branch)
#
# Windows PowerShell 5.1, pure ASCII. Never creates .runtime\maintenance-mode.
# Exit codes: 100 done / up to date, 101 stopped or cancelled (nothing changed), 102 failed.
param(
    [string]$GameRoot = 'C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale',
    [string]$StagingRoot = 'C:\Users\AM\Documents\jaspercraft-deploy',
    [string]$JdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot',
    [string]$Branch = 'main',
    # Show the plan and stop before the test server; changes nothing.
    [switch]$DryRun,
    # Do not wait for a key at the end (for local testing).
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DeployLogic.ps1')
. (Join-Path $PSScriptRoot 'DeployRuntime.ps1')

$script:ExitCode = 101

function Stop-Deploy {
    param([string]$Why, [string]$Level = 'Warn')
    Say $Why $Level
    throw 'DEPLOY_STOPPED'
}

function Show-List {
    param([string[]]$Items, [int]$Max = 15)
    $all = @($Items)
    $n = 0
    foreach ($i in $all) {
        if ($n -ge $Max) { Say ('    ... and ' + ($all.Count - $Max) + ' more'); break }
        Say ('    ' + $i)
        $n++
    }
}

function Invoke-Deploy {
    $ctx = $script:Ctx
    Say 'JasperCraft deployer' 'Step'
    Say 'This puts the newest approved changes from GitHub on your live server.'
    Say ('Live folder: ' + $ctx.Game)

    if (-not (Test-Path -LiteralPath (Join-Path $ctx.Game '.git'))) { Stop-Deploy ('The live folder was not found or is not a git folder: ' + $ctx.Game) 'Bad' }

    # ---- 1. fetch in the staging clone
    Say 'Checking GitHub for new changes...' 'Step'
    $originUrl = Get-GitText $ctx.Game @('remote', 'get-url', 'origin')
    if (-not (Test-Path -LiteralPath (Join-Path $ctx.Staging '.git'))) {
        Say ('First run: making a separate download folder at ' + $ctx.Staging + ' (one time, can take a few minutes)...')
        if (Test-Path -LiteralPath $ctx.Staging) {
            $existing = @(Get-ChildItem -LiteralPath $ctx.Staging -Force)
            if ($existing.Count -gt 0) { Stop-Deploy ('The folder ' + $ctx.Staging + ' already exists and is not a download folder. Please ask Claude.') }
        }
        $clone = Invoke-Git -Repo '' -Arguments @('clone', '--no-checkout', $originUrl, $ctx.Staging) -AllowFail
        if ($clone.ExitCode -ne 0) { Stop-Deploy ('Could not download from GitHub, so nothing was changed. Check the internet connection and try again. (' + $clone.Err.Trim() + ')') }
    } else {
        [void](Invoke-Git -Repo $ctx.Staging -Arguments @('remote', 'set-url', 'origin', $originUrl))
    }
    $fetch = Invoke-Git -Repo $ctx.Staging -Arguments @('fetch', '--prune', '--no-tags', 'origin', ('+refs/heads/' + $Branch + ':refs/remotes/origin/' + $Branch)) -AllowFail
    if ($fetch.ExitCode -ne 0) { Stop-Deploy ('Could not reach GitHub, so nothing was changed. Check the internet connection and try again. (' + $fetch.Err.Trim() + ')') }
    $target = Get-GitText $ctx.Staging @('rev-parse', ('refs/remotes/origin/' + $Branch))

    # ---- what is live now
    $state = Read-DeployState
    $gameHead = Get-GitText $ctx.Game @('rev-parse', 'HEAD')
    $baseline = $gameHead
    if ($null -ne $state -and $state.commit) {
        $baseline = [string]$state.commit
        if ($gameHead -ne $baseline) {
            if (-not (Test-GitCommitExists $ctx.Staging $gameHead)) {
                Stop-Deploy 'The live folder has changes that were never uploaded to GitHub. Nothing was changed. Please ask Claude to sort this out first.'
            }
            if ((Test-GitCommitExists $ctx.Staging $baseline) -and (Test-GitAncestor $ctx.Staging $baseline $gameHead)) {
                Write-DeployLog ('Live folder moved on since the last deploy (' + $baseline + ' -> ' + $gameHead + '); using it as the live version')
                $baseline = $gameHead
            } else {
                Stop-Deploy 'The live folder is not on the version the deployer put live last time. Nothing was changed. Please ask Claude to check it.'
            }
        }
    }
    if (-not (Test-GitCommitExists $ctx.Staging $baseline)) {
        Stop-Deploy 'The live version is not on GitHub (it has changes that were never uploaded). Nothing was changed. Please ask Claude to sort this out first.'
    }
    Write-DeployLog ('Live: ' + $baseline + '  GitHub ' + $Branch + ': ' + $target)

    if ($baseline -eq $target) {
        Say 'Already up to date. The live server has the newest version.' 'Good'
        $script:ExitCode = 100
        return
    }
    if (-not (Test-GitAncestor $ctx.Staging $baseline $target)) {
        Stop-Deploy 'GitHub''s version does not simply build on top of the live version (history was rewritten). Nothing was changed. Please ask Claude.'
    }

    # ---- 2. what would go live
    $commits = @((Get-GitText $ctx.Staging @('log', '--no-merges', '--format=%h  %s  (%an, %ar)', ($baseline + '..' + $target))) -split "`r?`n" | Where-Object { $_ })
    $diffText = (Invoke-Git -Repo $ctx.Staging -Arguments @('diff', '--name-status', '-z', '--no-renames', $baseline, $target)).Out
    $changes = ConvertFrom-NameStatusZ $diffText
    $plan = Get-DeployPlan $changes

    # the deployer itself changed: only allowed once a local Claude session has reviewed and installed it
    [void](Invoke-Git -Repo $ctx.Staging -Arguments @('checkout', '--force', '--detach', '-q', $target))
    [void](Invoke-Git -Repo $ctx.Staging -Arguments @('clean', '-fdxq'))
    $unreviewed = @()
    foreach ($r in $plan.Review) {
        $liveSha = Get-FileSha (Get-LocalPath $ctx.Game $r.Path)
        $newSha = Get-FileSha (Get-LocalPath $ctx.Staging $r.Path)
        if ($liveSha -ne $newSha) { $unreviewed += $r.Path }
    }
    if ($unreviewed.Count -gt 0) {
        Say 'These changes include changes to the deployer itself:' 'Warn'
        Show-List $unreviewed
        Stop-Deploy 'This needs a Claude review first: ask a Claude session on this PC to review and install the new deployer. Nothing was changed.'
    }
    if ($plan.Unsupported.Count -gt 0) {
        Say 'These changes remove a plugin:' 'Warn'
        Show-List @($plan.Unsupported | ForEach-Object { $_.Path })
        Stop-Deploy 'Removing plugins is not done automatically. Please ask Claude. Nothing was changed.'
    }

    # never overwrite uncommitted edits or files git does not know about in the live folder
    $modified = Get-GameModifiedPaths
    $clash = @()
    foreach ($c in @($plan.Site) + @($plan.Other)) {
        if ($modified -contains $c.Path) { $clash += ($c.Path + ' (edited on this PC and not saved to git)'); continue }
        if ($c.Status -eq 'A') {
            $live = Get-LocalPath $ctx.Game $c.Path
            if ((Test-Path -LiteralPath $live -PathType Leaf) -and -not (Test-GameTracked $c.Path)) {
                if ((Get-FileSha $live) -ne (Get-FileSha (Get-LocalPath $ctx.Staging $c.Path))) { $clash += ($c.Path + ' (a different file with this name already exists on this PC)') }
            }
        }
    }
    if ($clash.Count -gt 0) {
        Say 'Some files on this PC would be overwritten:' 'Warn'
        Show-List $clash
        Stop-Deploy 'To be safe, nothing was changed. Please ask Claude to look at these files.'
    }

    Say ('New on GitHub since the live version (' + $commits.Count + ' change(s)):') 'Step'
    Show-List $commits 20
    Say 'What goes live:' 'Step'
    if ($plan.Plugins.Count -gt 0) { Say ('Plugins: ' + ($plan.ChangedPlugins -join ', ') + '  (needs a server restart, about 1-3 minutes)') }
    if ($plan.Site.Count -gt 0) { Say ('Website: ' + $plan.Site.Count + ' file(s)  (no restart needed)') }
    if ($plan.Other.Count -gt 0) { Say ('Other files (source code, notes, tools): ' + $plan.Other.Count + ' - only the folder copy is updated, nothing restarts for these') }
    if (-not $plan.Deployable) { Say 'None of these changes affect the running game or website.' }

    $badHealth = Get-FailingHealthUrls
    if ($plan.Deployable -and $badHealth.Count -gt 0) {
        Stop-Deploy ('The server or website is not healthy right now (' + ($badHealth -join ', ') + '), so the deployer could not tell whether the update works. Nothing was changed. Try again later or ask Claude.')
    }

    if ($DryRun) { Say 'Dry run: stopping here. Nothing was changed.' 'Good'; $script:ExitCode = 100; return }

    if (-not (Read-YesNo 'Put these changes live now?')) { Say 'Cancelled. Nothing was changed.' 'Warn'; return }

    $summary = @()
    if ($plan.Plugins.Count -gt 0) { $summary += ('plugins ' + ($plan.ChangedPlugins -join ', ')) }
    if ($plan.Site.Count -gt 0) { $summary += ('' + $plan.Site.Count + ' website file(s)') }
    if ($plan.Other.Count -gt 0) { $summary += ('' + $plan.Other.Count + ' other file(s), folder copy only') }
    $summaryText = $summary -join '; '

    # ---- 3. Gate 1: test server
    if ($plan.Plugins.Count -gt 0) {
        Say 'Test 1 of 2: trying the new plugins on a separate test server (players are not affected)...' 'Step'
        $newJars = Get-LocalPath $ctx.Staging 'server/plugins'
        $gate = Invoke-TestServerGate -ChangedPlugins $plan.ChangedPlugins -NewJarsDir $newJars
        if (-not $gate.Ok) {
            Say ('The test server found a problem: ' + $gate.Reason) 'Bad'
            Publish-DeployStatus -Result 'BLOCKED by the test server (nothing went live)' -Commit $target -PreviousCommit $baseline -Summary $summaryText -Notes @($gate.Reason, 'Test server log kept at .runtime/deploy/last-testserver.log on the PC.') -LogText $gate.LogText
            Stop-Deploy 'Nothing went live; the server still runs the old version. Please tell Claude (it can read the deploy-status branch).' 'Bad'
        }
        Say 'The test server started fine with the new plugins.' 'Good'
    }

    # ---- 4. Gate 2: nobody online, not restarted in the last 5 minutes
    if ($plan.NeedsRestart) {
        Say 'Test 2 of 2: checking that nobody is playing right now...' 'Step'
        if (-not (Wait-RestartWindow)) { Stop-Deploy 'Cancelled while waiting. Nothing was changed.' }
        Say 'Nobody is online. Going ahead.' 'Good'
    }

    # ---- 5. deploy
    $ops = @()
    foreach ($c in @($plan.Plugins) + @($plan.Site)) {
        $src = $null
        if ($c.Status -ne 'D') { $src = Get-LocalPath $ctx.Staging $c.Path }
        $ops += New-Object PSObject -Property @{ Path = $c.Path; Kind = $c.Kind; Source = $src; Status = $c.Status }
    }
    $otherOps = @($plan.Other | ForEach-Object { New-Object PSObject -Property @{ Path = $_.Path; Kind = 'Other'; Source = $null; Status = $_.Status } })

    $res = $null
    if ($ops.Count -gt 0) {
        $res = Invoke-LiveChange -Operations $ops -BackupOnly $otherOps -FromCommit $baseline -ToCommit $target
    } elseif ($otherOps.Count -gt 0) {
        $backupDir = New-DeployBackup -Operations $otherOps -FromCommit $baseline -ToCommit $target
        Set-BackupStatus $backupDir 'deployed'
        $res = New-Object PSObject -Property @{ Ok = $true; Reason = ''; LogText = ''; BackupDir = $backupDir; RolledBack = $false; RollbackOk = $false; RollbackReason = '' }
    }

    if (-not $res.Ok) {
        $notes = @('Failed: ' + $res.Reason)
        if ($res.RollbackOk) {
            $notes += 'The previous version was restored and is running again.'
            Publish-DeployStatus -Result 'FAILED - previous version restored' -Commit $target -PreviousCommit $baseline -Summary $summaryText -Notes $notes -LogText $res.LogText
            Say ('The update did NOT go live: ' + $res.Reason) 'Bad'
            Say 'The old version is back and running, so players can keep playing. Please tell Claude (it can read the deploy-status branch).' 'Warn'
        } else {
            $notes += ('Restoring the previous version ALSO failed: ' + $res.RollbackReason)
            Publish-DeployStatus -Result 'FAILED - restore of the previous version also failed' -Commit $target -PreviousCommit $baseline -Summary $summaryText -Notes $notes -LogText $res.LogText
            Say ('The update did NOT go live: ' + $res.Reason) 'Bad'
            Say ('The old files were put back, but the server did not come back cleanly either: ' + $res.RollbackReason) 'Bad'
            Say ('Please ask Claude for help. The backup is in ' + $res.BackupDir) 'Bad'
        }
        $script:ExitCode = 102
        return
    }

    # ---- 6. record and report
    Save-DeployState @{ commit = $target; previousCommit = $baseline; deployedAt = (Get-Date).ToUniversalTime().ToString('o'); lastResult = 'success'; backup = $res.BackupDir }
    try {
        Sync-GameGit -Commit $target -Changes $changes
    } catch {
        Write-DeployLog ('Git sync of the live folder failed: ' + $_.Exception.Message)
        Say 'The update is live, but updating the folder''s git copy had a problem (the game is fine). Please mention it to Claude.' 'Warn'
    }
    Publish-DeployStatus -Result 'SUCCESS' -Commit $target -PreviousCommit $baseline -Summary $summaryText -Notes @() -LogText $res.LogText
    Say 'Done! The update is live.' 'Good'
    if ($plan.Plugins.Count -gt 0) { Say ('Plugins now live: ' + ($plan.ChangedPlugins -join ', ')) 'Good' }
    if ($plan.Site.Count -gt 0) { Say ('Website files now live: ' + $plan.Site.Count + ' (players may need to refresh the page)') 'Good' }
    Say 'If something seems wrong, double-click "Undo last deploy.bat".'
    $script:ExitCode = 100
}

$ctxReady = $false
try {
    [void](Initialize-DeployContext -GameRoot $GameRoot -StagingRoot $StagingRoot -JdkRoot $JdkRoot)
    $ctxReady = $true
    if (-not (Enter-DeployLock)) {
        Say 'The deployer (or Undo) is already running in another window. Close that one first.' 'Warn'
    } else {
        Write-DeployLog ('==== Deploy started' + $(if ($DryRun) { ' (dry run)' } else { '' }))
        Invoke-Deploy
    }
} catch {
    if ($_.Exception.Message -ne 'DEPLOY_STOPPED') {
        if ($ctxReady) { Write-DeployLog ('Unexpected error: ' + $_.Exception.Message + "`n" + $_.ScriptStackTrace) }
        Write-Host ('  Something unexpected went wrong: ' + $_.Exception.Message) -ForegroundColor Red
        Write-Host '  If this happened before "Putting the new files in place", nothing was changed. Please tell Claude.' -ForegroundColor Red
        $script:ExitCode = 102
    }
} finally {
    if ($ctxReady) { Write-DeployLog ('==== Deploy finished, exit code ' + $script:ExitCode) }
    Exit-DeployLock
    if ($ctxReady) { Write-Host ('  Full log: ' + $script:Ctx.LogFile) -ForegroundColor DarkGray }
    if (-not $NoPause) { Wait-AnyKey }
}
exit $script:ExitCode
