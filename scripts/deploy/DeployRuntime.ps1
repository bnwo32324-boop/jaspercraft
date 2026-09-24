# JasperCraft deployer: shared runtime helpers (files, git, server, test server, status).
# Dot-sourced by Deploy-JasperCraft.ps1 and Undo-LastDeploy.ps1 after DeployLogic.ps1.
# Windows PowerShell 5.1 compatible, pure ASCII. Never creates .runtime\maintenance-mode.

$script:Utf8NoBom = New-Object System.Text.UTF8Encoding $false
$script:Ctx = $null
$script:LockStream = $null

function Initialize-DeployContext {
    param([string]$GameRoot, [string]$StagingRoot, [string]$JdkRoot)
    $runtime = Join-Path $GameRoot '.runtime'
    $deployDir = Join-Path $runtime 'deploy'
    $script:Ctx = New-Object PSObject -Property @{
        Game        = $GameRoot
        Staging     = $StagingRoot
        Jdk         = $JdkRoot
        Java        = (Join-Path (Join-Path $JdkRoot 'bin') 'java.exe')
        Runtime     = $runtime
        DeployDir   = $deployDir
        LogFile     = (Join-Path $deployDir 'deploy.log')
        StateFile   = (Join-Path $deployDir 'state.json')
        LockFile    = (Join-Path $deployDir 'deploy.lock')
        Plugins     = (Join-Path (Join-Path $GameRoot 'server') 'plugins')
        Update      = (Join-Path (Join-Path (Join-Path $GameRoot 'server') 'plugins') 'update')
        LatestLog   = (Join-Path (Join-Path (Join-Path $GameRoot 'server') 'logs') 'latest.log')
        StopRequest = (Join-Path $runtime 'game-server-stop.request')
        Identity    = (Join-Path $runtime 'paper-server.process.json')
        StatusUrl   = 'http://127.0.0.1:3310/status'
        HealthUrls  = @('http://127.0.0.1:3200/api/health', 'http://127.0.0.1:3199/')
        Git         = (Find-GitExe)
        RestartTimeoutSec = 180
        RestartGap  = [TimeSpan]::FromMinutes(5)
        TestPort    = 25597
    }
    if (-not (Test-Path -LiteralPath $deployDir)) { New-Item -ItemType Directory -Force -Path $deployDir | Out-Null }
    return $script:Ctx
}

function Find-GitExe {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @('C:\Program Files\Git\cmd\git.exe', 'C:\Program Files (x86)\Git\cmd\git.exe')) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    return 'git'
}

# ---------------------------------------------------------------- messages and log

function Write-DeployLog {
    param([string]$Text)
    if ($null -eq $script:Ctx) { return }
    try {
        $line = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss') + '  ' + $Text + "`r`n"
        [IO.File]::AppendAllText($script:Ctx.LogFile, $line, $script:Utf8NoBom)
    } catch {}
}

# Says something to the owner (and logs it). Levels: Info, Step, Good, Warn, Bad.
function Say {
    param([string]$Text, [string]$Level = 'Info')
    $color = 'Gray'
    $prefix = '  '
    switch ($Level) {
        'Step' { $color = 'Cyan'; $prefix = '' ; Write-Host '' }
        'Good' { $color = 'Green' }
        'Warn' { $color = 'Yellow' }
        'Bad'  { $color = 'Red' }
    }
    Write-Host ($prefix + $Text) -ForegroundColor $color
    Write-DeployLog ($Level.ToUpper() + ' ' + $Text)
}

function Write-StatusLine {
    param([string]$Text)
    $width = 79
    try { $width = [Math]::Max(40, $Host.UI.RawUI.WindowSize.Width - 1) } catch {}
    if ($Text.Length -gt $width) { $Text = $Text.Substring(0, $width) }
    Write-Host ("`r" + $Text.PadRight($width)) -NoNewline -ForegroundColor Gray
}

function Clear-StatusLine { Write-Host '' }

function Test-KeyPressed {
    try {
        if ([Console]::KeyAvailable) {
            while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) }
            return $true
        }
    } catch {}
    return $false
}

function Wait-AnyKey {
    Write-Host ''
    Write-Host 'Press any key to close' -ForegroundColor White
    try {
        while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) }
    } catch {}
    try { [void]$Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown') } catch { try { [void](Read-Host) } catch {} }
}

function Read-YesNo {
    param([string]$Question)
    while ($true) {
        Write-Host ''
        $answer = Read-Host ($Question + ' Type Y for yes or N for no, then press Enter')
        if ($null -eq $answer) { return $false }
        $a = $answer.Trim().ToLowerInvariant()
        if ($a -eq 'y' -or $a -eq 'yes') { Write-DeployLog 'Owner answered YES'; return $true }
        if ($a -eq 'n' -or $a -eq 'no') { Write-DeployLog 'Owner answered NO'; return $false }
        Write-Host '  Please type Y or N.' -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------------- single instance lock

function Enter-DeployLock {
    try {
        $script:LockStream = New-Object IO.FileStream($script:Ctx.LockFile, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        return $true
    } catch {
        $script:LockStream = $null
        return $false
    }
}

function Exit-DeployLock {
    if ($null -ne $script:LockStream) {
        try { $script:LockStream.Dispose() } catch {}
        $script:LockStream = $null
    }
}

# ---------------------------------------------------------------- processes and git

function Invoke-Native {
    param([string]$FilePath, [string[]]$Arguments, [string]$InputText, [hashtable]$Environment, [string]$WorkingDirectory)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = Join-CommandLine $Arguments
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardInput = $true
    $psi.StandardOutputEncoding = $script:Utf8NoBom
    $psi.StandardErrorEncoding = $script:Utf8NoBom
    if ($WorkingDirectory) { $psi.WorkingDirectory = $WorkingDirectory }
    $psi.EnvironmentVariables['GIT_TERMINAL_PROMPT'] = '0'
    if ($Environment) { foreach ($k in $Environment.Keys) { $psi.EnvironmentVariables[$k] = [string]$Environment[$k] } }
    $p = [System.Diagnostics.Process]::Start($psi)
    $errTask = $p.StandardError.ReadToEndAsync()
    $outTask = $p.StandardOutput.ReadToEndAsync()
    if ($InputText) { $p.StandardInput.Write($InputText) }
    $p.StandardInput.Close()
    $p.WaitForExit()
    $result = New-Object PSObject -Property @{
        ExitCode = $p.ExitCode
        Out      = $outTask.Result
        Err      = $errTask.Result
    }
    $p.Dispose()
    return $result
}

function Invoke-Git {
    param([string]$Repo, [string[]]$Arguments, [switch]$AllowFail, [string]$InputText, [hashtable]$Environment)
    $all = @()
    if ($Repo) { $all += @('-C', $Repo) }
    $all += $Arguments
    $r = Invoke-Native -FilePath $script:Ctx.Git -Arguments $all -InputText $InputText -Environment $Environment
    Write-DeployLog ('git ' + ($all -join ' ') + ' -> ' + $r.ExitCode)
    if ($r.ExitCode -ne 0 -and -not $AllowFail) {
        $msg = ($r.Err + ' ' + $r.Out).Trim()
        Write-DeployLog ('git error: ' + $msg)
        throw ('git ' + ($Arguments -join ' ') + ' failed: ' + $msg)
    }
    return $r
}

function Get-GitText {
    param([string]$Repo, [string[]]$Arguments)
    return (Invoke-Git -Repo $Repo -Arguments $Arguments).Out.Trim()
}

function Test-GitAncestor {
    param([string]$Repo, [string]$Ancestor, [string]$Descendant)
    $r = Invoke-Git -Repo $Repo -Arguments @('merge-base', '--is-ancestor', $Ancestor, $Descendant) -AllowFail
    return ($r.ExitCode -eq 0)
}

function Test-GitCommitExists {
    param([string]$Repo, [string]$Commit)
    $r = Invoke-Git -Repo $Repo -Arguments @('cat-file', '-e', ($Commit + '^{commit}')) -AllowFail
    return ($r.ExitCode -eq 0)
}

# ---------------------------------------------------------------- files

function Get-LocalPath {
    param([string]$Root, [string]$RepoPath)
    $rel = (ConvertTo-RepoPath $RepoPath) -replace '/', [IO.Path]::DirectorySeparatorChar
    return [IO.Path]::Combine($Root, $rel)
}

function Get-FileSha {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Read-SharedText {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $fs = $null
    try {
        $fs = New-Object IO.FileStream($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        $sr = New-Object IO.StreamReader($fs, $script:Utf8NoBom)
        return $sr.ReadToEnd()
    } catch {
        return ''
    } finally {
        if ($null -ne $fs) { $fs.Dispose() }
    }
}

# Identifies one Paper run's latest.log by its first bytes (they start with a timestamp).
function Get-LogFingerprint {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $fs = $null
    try {
        $fs = New-Object IO.FileStream($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        $buf = New-Object byte[] 512
        $n = $fs.Read($buf, 0, 512)
        return [Convert]::ToBase64String($buf, 0, $n)
    } catch {
        return ''
    } finally {
        if ($null -ne $fs) { $fs.Dispose() }
    }
}

function Copy-FileSafely {
    param([string]$Source, [string]$Destination)
    $dir = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $tmp = $Destination + '.deploying'
    [IO.File]::Copy($Source, $tmp, $true)
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        try {
            if (Test-Path -LiteralPath $Destination) {
                [IO.File]::Copy($tmp, $Destination, $true)
                [IO.File]::Delete($tmp)
            } else {
                [IO.File]::Move($tmp, $Destination)
            }
            return
        } catch {
            if ($attempt -eq 10) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Remove-FileSafely {
    param([string]$Path)
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        try {
            if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
            return
        } catch {
            if ($attempt -eq 10) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Write-JsonFile {
    param([string]$Path, $Object)
    $json = $Object | ConvertTo-Json -Depth 6
    [IO.File]::WriteAllText($Path, $json, $script:Utf8NoBom)
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    try { return ([IO.File]::ReadAllText($Path) | ConvertFrom-Json) } catch { return $null }
}

function Read-DeployState { return (Read-JsonFile $script:Ctx.StateFile) }

function Save-DeployState {
    param([hashtable]$Changes)
    $old = Read-DeployState
    $state = @{}
    if ($null -ne $old) { foreach ($p in $old.PSObject.Properties) { $state[$p.Name] = $p.Value } }
    foreach ($k in $Changes.Keys) { $state[$k] = $Changes[$k] }
    Write-JsonFile $script:Ctx.StateFile $state
}

# ---------------------------------------------------------------- live plugin and site files

# New jar for an existing live plugin goes to plugins\update\ (Paper swaps it in at the next start;
# the live jar is locked while the server runs). A brand-new plugin jar is not locked: copy it directly.
function Install-PluginJar {
    param([string]$Source, [string]$RepoPath)
    $name = (ConvertTo-RepoPath $RepoPath).Split('/')[-1]
    $live = Join-Path $script:Ctx.Plugins $name
    if (Test-Path -LiteralPath $live) {
        if (-not (Test-Path -LiteralPath $script:Ctx.Update)) { New-Item -ItemType Directory -Force -Path $script:Ctx.Update | Out-Null }
        Copy-FileSafely $Source (Join-Path $script:Ctx.Update $name)
        return 'staged'
    }
    Copy-FileSafely $Source $live
    return 'added'
}

# Applies one operation {Path; Kind; Source ($null = delete)}. Returns a short word for the log.
function Invoke-FileOperation {
    param($Op)
    $target = Get-LocalPath $script:Ctx.Game $Op.Path
    if ($Op.Kind -eq 'Plugin') {
        if ($null -eq $Op.Source) {
            # Remove a plugin that did not exist before: drop any staged copy; the live file is removed
            # after the server stops (it is locked while running).
            $name = (ConvertTo-RepoPath $Op.Path).Split('/')[-1]
            Remove-FileSafely (Join-Path $script:Ctx.Update $name)
            try { Remove-Item -LiteralPath $target -Force -ErrorAction Stop; return 'removed' } catch { return 'remove-after-stop' }
        }
        return (Install-PluginJar $Op.Source $Op.Path)
    }
    if ($null -eq $Op.Source) { Remove-FileSafely $target; return 'removed' }
    Copy-FileSafely $Op.Source $target
    return 'copied'
}

# ---------------------------------------------------------------- backups

# Copies every live file an operation list will change into backup-<time>\files\ with a manifest.
function New-DeployBackup {
    param([object[]]$Operations, [string]$FromCommit, [string]$ToCommit, [string]$Prefix = 'backup')
    $stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
    $dir = Join-Path $script:Ctx.DeployDir ($Prefix + '-' + $stamp)
    $filesDir = Join-Path $dir 'files'
    New-Item -ItemType Directory -Force -Path $filesDir | Out-Null
    $entries = @()
    foreach ($op in @($Operations)) {
        $live = Get-LocalPath $script:Ctx.Game $op.Path
        $existed = (Test-Path -LiteralPath $live -PathType Leaf)
        if ($existed) {
            $copy = Get-LocalPath $filesDir $op.Path
            $copyDir = Split-Path -Parent $copy
            if (-not (Test-Path -LiteralPath $copyDir)) { New-Item -ItemType Directory -Force -Path $copyDir | Out-Null }
            [IO.File]::Copy($live, $copy, $true)
        }
        $entries += @{ path = $op.Path; kind = $op.Kind; existed = $existed }
    }
    $manifest = @{
        created    = (Get-Date).ToString('o')
        fromCommit = $FromCommit
        toCommit   = $ToCommit
        status     = 'in-progress'
        entries    = $entries
    }
    Write-JsonFile (Join-Path $dir 'manifest.json') $manifest
    Write-DeployLog ('Backup made: ' + $dir + ' (' + $entries.Count + ' files)')
    return $dir
}

function Set-BackupStatus {
    param([string]$BackupDir, [string]$Status)
    $path = Join-Path $BackupDir 'manifest.json'
    $m = Read-JsonFile $path
    if ($null -eq $m) { return }
    $m.status = $Status
    Write-JsonFile $path $m
}

# Operations that put a backup back.
function Get-RestoreOperations {
    param([string]$BackupDir)
    $m = Read-JsonFile (Join-Path $BackupDir 'manifest.json')
    $ops = @()
    foreach ($e in @($m.entries)) {
        $src = $null
        if ($e.existed) { $src = Get-LocalPath (Join-Path $BackupDir 'files') $e.path }
        $ops += New-Object PSObject -Property @{ Path = $e.path; Kind = $e.kind; Source = $src; Status = 'R' }
    }
    return ,$ops
}

# ---------------------------------------------------------------- server status and restart

function Get-ServerStatus {
    try {
        return (Invoke-RestMethod -Uri $script:Ctx.StatusUrl -TimeoutSec 4 -UseBasicParsing)
    } catch {
        return $null
    }
}

function Test-HttpOk {
    param([string]$Url)
    try {
        $r = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
        return ($r.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Get-FailingHealthUrls {
    $bad = @()
    foreach ($u in $script:Ctx.HealthUrls) { if (-not (Test-HttpOk $u)) { $bad += $u } }
    return ,$bad
}

function Get-LastRestartTime {
    $times = @()
    if (Test-Path -LiteralPath $script:Ctx.Identity) { $times += (Get-Item -LiteralPath $script:Ctx.Identity).LastWriteTime }
    $state = Read-DeployState
    if ($null -ne $state -and $state.lastRestartRequestedAt) {
        try { $times += [datetime]::Parse($state.lastRestartRequestedAt, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind).ToLocalTime() } catch {}
    }
    if ($times.Count -eq 0) { return $null }
    return ($times | Sort-Object -Descending | Select-Object -First 1)
}

# Gate 2: waits until nobody is online and the server has run at least 5 minutes. Never kicks anyone.
# Returns $true when it is fine to restart, $false if the owner pressed a key to cancel.
function Wait-RestartWindow {
    $shown = $false
    while ($true) {
        $status = Get-ServerStatus
        $reason = Get-RestartWaitReason -Status $status -LastRestart (Get-LastRestartTime) -Now (Get-Date) -MinGap $script:Ctx.RestartGap
        if ($null -eq $reason) {
            if ($shown) { Clear-StatusLine }
            return $true
        }
        if (-not $shown) {
            Say ('Waiting before restarting: ' + $reason + '. Nobody will be kicked.') 'Warn'
            Say 'The deploy starts by itself as soon as it is safe. Press any key to cancel instead.'
            $shown = $true
        }
        for ($i = 15; $i -gt 0; $i--) {
            Write-StatusLine ('  Waiting: ' + $reason + '. Checking again in ' + $i + 's (any key = cancel)')
            if (Test-KeyPressed) { Clear-StatusLine; Write-DeployLog 'Owner cancelled while waiting to restart'; return $false }
            Start-Sleep -Seconds 1
        }
    }
}

# Asks the supervisor to restart Paper (it restarts it by itself after "stop").
function Request-ServerRestart {
    $now = Get-Date
    [IO.File]::WriteAllText($script:Ctx.StopRequest, "stop`r`n", (New-Object System.Text.ASCIIEncoding))
    Save-DeployState @{ lastRestartRequestedAt = $now.ToUniversalTime().ToString('o') }
    Write-DeployLog 'Restart requested (game-server-stop.request)'
    return $now
}

# Waits for a NEW latest.log with 'Done (' and the READY lines, swapped jars and both health URLs.
# RemoveAfterStop: jars to delete once the old server has let go of them.
function Wait-LiveStart {
    param([datetime]$Since, [string]$OldFingerprint, [string[]]$Plugins, [string[]]$StagedJars, [string[]]$RemoveAfterStop)
    $tokens = Get-RequiredReadyTokens -Plugins $Plugins -Context 'Live'
    $deadline = (Get-Date).AddSeconds($script:Ctx.RestartTimeoutSec)
    $pendingRemovals = New-Object System.Collections.ArrayList
    foreach ($j in @($RemoveAfterStop)) { if ($j) { [void]$pendingRemovals.Add($j) } }
    $waitingFor = 'the old server to stop'
    $text = ''
    $isNew = $false
    while ((Get-Date) -lt $deadline) {
        foreach ($j in @($pendingRemovals)) {
            try { Remove-Item -LiteralPath $j -Force -ErrorAction Stop; $pendingRemovals.Remove($j); Write-DeployLog ('Removed ' + $j) } catch {}
        }
        $fp = Get-LogFingerprint $script:Ctx.LatestLog
        $isNew = $false
        if ($fp -and $fp -ne $OldFingerprint) {
            $isNew = ((Get-Item -LiteralPath $script:Ctx.LatestLog).LastWriteTime -ge $Since.AddSeconds(-2))
        }
        if ($isNew) {
            $text = Read-SharedText $script:Ctx.LatestLog
            $lines = $text -split "`r?`n"
            $failures = Find-PluginLoadFailures -Lines $lines -Plugins $Plugins
            if ($failures.Count -gt 0) {
                Clear-StatusLine
                return (New-StartResult $false ('a plugin failed to start: ' + $failures[0]) $text)
            }
            if ($text.IndexOf('Done (', [StringComparison]::Ordinal) -lt 0) {
                $waitingFor = 'the server to finish starting'
            } else {
                $missing = Get-MissingTokens -LogText $text -Tokens $tokens
                $stillStaged = @($StagedJars | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $script:Ctx.Update $_)) })
                if ($missing.Count -gt 0) {
                    $waitingFor = 'ready lines: ' + ($missing -join ', ')
                } elseif ($stillStaged.Count -gt 0) {
                    $waitingFor = 'the new plugin files to be picked up'
                } elseif ($pendingRemovals.Count -gt 0) {
                    $waitingFor = 'an old plugin file to be removed'
                } else {
                    $bad = Get-FailingHealthUrls
                    if ($bad.Count -eq 0) { Clear-StatusLine; return (New-StartResult $true '' $text) }
                    $waitingFor = 'the website health check'
                }
            }
        }
        $left = [int]($deadline - (Get-Date)).TotalSeconds
        Write-StatusLine ('  Restarting... waiting for ' + $waitingFor + ' (' + $left + 's left)')
        Start-Sleep -Seconds 2
    }
    Clear-StatusLine
    if (-not $isNew) { return (New-StartResult $false 'the server did not restart within 3 minutes' $text) }
    $missingNow = Get-MissingTokens -LogText $text -Tokens $tokens
    if ($text.IndexOf('Done (', [StringComparison]::Ordinal) -lt 0) { return (New-StartResult $false 'the server did not finish starting within 3 minutes' $text) }
    if ($missingNow.Count -gt 0) { return (New-StartResult $false ('these plugins did not report ready: ' + ($missingNow -join ', ')) $text) }
    return (New-StartResult $false ('timed out waiting for ' + $waitingFor) $text)
}

function New-StartResult {
    param([bool]$Ok, [string]$Reason, [string]$LogText)
    return (New-Object PSObject -Property @{ Ok = $Ok; Reason = $Reason; LogText = $LogText })
}

# Site-only changes: no restart, the website just has to stay healthy.
function Wait-SiteHealthy {
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        $bad = Get-FailingHealthUrls
        if ($bad.Count -eq 0) { return (New-StartResult $true '' '') }
        Start-Sleep -Seconds 3
    }
    return (New-StartResult $false ('the website health check failed: ' + ((Get-FailingHealthUrls) -join ', ')) '')
}

# Applies operations with a backup; verifies; restores the backup (and restarts once more) on failure.
# Returns {Ok; Reason; LogText; BackupDir; RolledBack; RollbackOk}.
function Invoke-LiveChange {
    param([object[]]$Operations, [object[]]$BackupOnly, [string]$FromCommit, [string]$ToCommit, [string]$BackupPrefix = 'backup')
    $pluginOps = @($Operations | Where-Object { $_.Kind -eq 'Plugin' })
    $needsRestart = ($pluginOps.Count -gt 0)
    $plugins = @($pluginOps | Where-Object { $null -ne $_.Source } | ForEach-Object { Get-PluginNameFromPath $_.Path })

    Say 'Making a backup of every file that is about to change...' 'Step'
    $backup = New-DeployBackup -Operations (@($Operations) + @($BackupOnly | Where-Object { $null -ne $_ })) -FromCommit $FromCommit -ToCommit $ToCommit -Prefix $BackupPrefix
    Say ('Backup saved in ' + $backup)

    $result = $null
    try {
        Say 'Putting the files in place...' 'Step'
        $staged = @()
        $removeLater = @()
        $oldFingerprint = Get-LogFingerprint $script:Ctx.LatestLog
        foreach ($op in @($Operations)) {
            $how = Invoke-FileOperation $op
            Write-DeployLog ('  ' + $how + ' ' + $op.Path)
            if ($how -eq 'staged') { $staged += (ConvertTo-RepoPath $op.Path).Split('/')[-1] }
            if ($how -eq 'remove-after-stop') { $removeLater += (Get-LocalPath $script:Ctx.Game $op.Path) }
        }
        Say ('' + @($Operations).Count + ' file(s) in place.')
        if ($needsRestart) {
            Say 'Restarting the game server so the plugin changes load (takes 1-3 minutes)...' 'Step'
            $since = Request-ServerRestart
            $result = Wait-LiveStart -Since $since -OldFingerprint $oldFingerprint -Plugins $plugins -StagedJars $staged -RemoveAfterStop $removeLater
        } else {
            Say 'No plugin changes, so no restart is needed. Checking the website...' 'Step'
            $result = Wait-SiteHealthy
        }
    } catch {
        $result = New-StartResult $false ('something went wrong while copying files: ' + $_.Exception.Message) ''
    }

    $out = New-Object PSObject -Property @{
        Ok = $result.Ok; Reason = $result.Reason; LogText = $result.LogText
        BackupDir = $backup; RolledBack = $false; RollbackOk = $false; RollbackReason = ''
    }
    if ($result.Ok) { Set-BackupStatus $backup 'deployed'; return $out }

    Say ('It did not work: ' + $result.Reason) 'Bad'
    Say 'Putting the previous version back from the backup...' 'Step'
    $out.RolledBack = $true
    try {
        $restore = Get-RestoreOperations $backup
        $staged = @()
        $removeLater = @()
        $oldFingerprint = Get-LogFingerprint $script:Ctx.LatestLog
        foreach ($op in $restore) {
            $how = Invoke-FileOperation $op
            Write-DeployLog ('  restore ' + $how + ' ' + $op.Path)
            if ($how -eq 'staged') { $staged += (ConvertTo-RepoPath $op.Path).Split('/')[-1] }
            if ($how -eq 'remove-after-stop') { $removeLater += (Get-LocalPath $script:Ctx.Game $op.Path) }
        }
        $restoredPlugins = @($restore | Where-Object { $_.Kind -eq 'Plugin' -and $null -ne $_.Source } | ForEach-Object { Get-PluginNameFromPath $_.Path })
        if ($needsRestart) {
            Say 'Restarting the game server once more with the previous version...' 'Step'
            $since = Request-ServerRestart
            $back = Wait-LiveStart -Since $since -OldFingerprint $oldFingerprint -Plugins $restoredPlugins -StagedJars $staged -RemoveAfterStop $removeLater
        } else {
            $back = Wait-SiteHealthy
        }
        $out.RollbackOk = $back.Ok
        $out.RollbackReason = $back.Reason
        if ($back.LogText) { $out.LogText = $back.LogText }
    } catch {
        $out.RollbackOk = $false
        $out.RollbackReason = $_.Exception.Message
    }
    if ($out.RollbackOk) { Set-BackupStatus $backup 'rolled-back' } else { Set-BackupStatus $backup 'rollback-failed' }
    return $out
}

# ---------------------------------------------------------------- Gate 1: test server

function Test-PortFree {
    param([int]$Port)
    $l = $null
    try {
        $l = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
        $l.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $l) { try { $l.Stop() } catch {} }
    }
}

# Starts a throwaway copy of the test server with the live Jaspr jars plus the new ones and checks it.
# Returns {Ok; Reason; LogText}.
function Invoke-TestServerGate {
    param([string[]]$ChangedPlugins, [string]$NewJarsDir)
    $template = Get-LocalPath $script:Ctx.Staging 'candidate/structure-audit/testserver-template'
    if (-not (Test-Path -LiteralPath $template)) { return (New-StartResult $false 'the test server template is missing from the repository' '') }
    if (-not (Test-Path -LiteralPath $script:Ctx.Java)) { return (New-StartResult $false ('Java was not found at ' + $script:Ctx.Java) '') }
    if (-not (Test-PortFree $script:Ctx.TestPort)) { return (New-StartResult $false ('port ' + $script:Ctx.TestPort + ' is busy (is another test server still running?)') '') }

    $root = Join-Path ([IO.Path]::GetTempPath()) ('jaspercraft-testserver-' + (Get-Date).ToString('yyyyMMdd-HHmmss'))
    $server = Join-Path $root 'server'
    $proc = $null
    $text = ''
    try {
        Say 'Copying the test server...'
        New-Item -ItemType Directory -Force -Path $root | Out-Null
        Copy-Item -LiteralPath $template -Destination $server -Recurse -Force
        $props = Join-Path $server 'server.properties'
        $p = [IO.File]::ReadAllText($props)
        $p = [regex]::Replace($p, '(?m)^server-port=.*$', ('server-port=' + $script:Ctx.TestPort))
        [IO.File]::WriteAllText($props, $p, $script:Utf8NoBom)
        $pluginsDir = Join-Path $server 'plugins'
        if (-not (Test-Path -LiteralPath $pluginsDir)) { New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null }
        $loaded = @()
        foreach ($jar in @(Get-ChildItem -LiteralPath $script:Ctx.Plugins -Filter 'Jaspr*.jar' -File)) {
            $name = $jar.BaseName
            if ($script:TestServerExcludedPlugins -contains $name) { continue }
            Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $pluginsDir $jar.Name) -Force
            $loaded += $name
        }
        foreach ($name in @($ChangedPlugins)) {
            if ($script:TestServerExcludedPlugins -contains $name) { continue }
            Copy-Item -LiteralPath (Join-Path $NewJarsDir ($name + '.jar')) -Destination (Join-Path $pluginsDir ($name + '.jar')) -Force
            if ($loaded -notcontains $name) { $loaded += $name }
        }
        Write-DeployLog ('Test server plugins: ' + ($loaded -join ', '))

        $javaArgs = @('-Xmx2G', '-Xms1G', '-XX:+UseG1GC',
            '-Dcom.mojang.eula.agree=true', '-DIReallyKnowWhatIAmDoingISwear=true',
            '-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=9', '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9',
            '-Djava.net.preferIPv4Stack=true', '-Dfile.encoding=UTF-8',
            '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Djline.terminal=jline.UnsupportedTerminal',
            '-Djaspr.gear.selftest=true',
            '-jar', 'paper-1.12.2.jar', 'nogui')
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $script:Ctx.Java
        $psi.Arguments = Join-CommandLine $javaArgs
        $psi.WorkingDirectory = $server
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardInput = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        Say ('Starting the test server on port ' + $script:Ctx.TestPort + ' (this can take a few minutes)...')
        $proc = [System.Diagnostics.Process]::Start($psi)
        # Drain the console so Java never blocks; the checks read logs\latest.log.
        $proc.BeginOutputReadLine()
        $proc.BeginErrorReadLine()

        $tokens = Get-RequiredReadyTokens -Plugins $ChangedPlugins -Context 'Test'
        $wantSelftest = ($ChangedPlugins -contains 'JasprGear')
        $log = Join-Path (Join-Path $server 'logs') 'latest.log'
        $start = Get-Date
        $deadline = $start.AddMinutes(8)
        $doneAt = $null
        $verdict = $null
        while ($null -eq $verdict) {
            Start-Sleep -Seconds 2
            $text = Read-SharedText $log
            $elapsed = Format-Duration ((Get-Date) - $start)
            if ($text.IndexOf('GEAR_SELFTEST FAIL', [StringComparison]::Ordinal) -ge 0) { $verdict = 'JasprGear self-test FAILED'; break }
            $hasDone = ($text.IndexOf('Done (', [StringComparison]::Ordinal) -ge 0)
            if ($hasDone -and $null -eq $doneAt) { $doneAt = Get-Date }
            $missing = Get-MissingTokens -LogText $text -Tokens $tokens
            $selftestOk = (-not $wantSelftest) -or ($text.IndexOf('GEAR_SELFTEST PASS', [StringComparison]::Ordinal) -ge 0)
            if ($hasDone -and $missing.Count -eq 0 -and $selftestOk) { Start-Sleep -Seconds 5; $text = Read-SharedText $log; $verdict = 'ok'; break }
            if ($proc.HasExited) { $verdict = 'the test server stopped by itself before it was ready'; break }
            if ($null -ne $doneAt -and ((Get-Date) - $doneAt).TotalSeconds -gt 90) {
                if ($missing.Count -gt 0) { $verdict = 'these plugins never reported ready: ' + ($missing -join ', ') }
                else { $verdict = 'the JasprGear self-test did not report PASS' }
                break
            }
            if ((Get-Date) -gt $deadline) { $verdict = 'the test server did not finish starting within 8 minutes'; break }
            if ($hasDone) { Write-StatusLine ('  Test server started, waiting for the plugins to report ready... ' + $elapsed) }
            else { Write-StatusLine ('  Test server starting... ' + $elapsed) }
        }
        Clear-StatusLine
        if ($verdict -ne 'ok') { return (New-StartResult $false $verdict $text) }

        $lines = $text -split "`r?`n"
        $checkPlugins = @($ChangedPlugins | Where-Object { $script:TestServerExcludedPlugins -notcontains $_ -and $script:TestServerExpectedFailures -notcontains $_ })
        $failures = Find-PluginLoadFailures -Lines $lines -Plugins $checkPlugins
        if ($failures.Count -gt 0) { return (New-StartResult $false ('a plugin failed to start: ' + $failures[0]) $text) }
        $problems = Find-JasprProblems -Lines $lines -IgnorePlugins $script:TestServerExpectedFailures
        if ($problems.Count -gt 0) { return (New-StartResult $false ('a Jaspr plugin logged an error: ' + $problems[0]) $text) }
        return (New-StartResult $true '' $text)
    } catch {
        Clear-StatusLine
        return (New-StartResult $false ('the test server could not be run: ' + $_.Exception.Message) $text)
    } finally {
        if ($null -ne $proc) {
            try {
                if (-not $proc.HasExited) {
                    Say 'Stopping the test server...'
                    $proc.StandardInput.WriteLine('stop')
                    $proc.StandardInput.Flush()
                    if (-not $proc.WaitForExit(90000)) { $proc.Kill(); [void]$proc.WaitForExit(15000) }
                }
            } catch { try { $proc.Kill() } catch {} }
            $proc.Dispose()
        }
        try {
            $copyLog = Join-Path (Join-Path $server 'logs') 'latest.log'
            if (Test-Path -LiteralPath $copyLog) { [IO.File]::Copy($copyLog, (Join-Path $script:Ctx.DeployDir 'last-testserver.log'), $true) }
        } catch {}
        for ($attempt = 1; $attempt -le 10; $attempt++) {
            try {
                if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop }
                break
            } catch { Start-Sleep -Seconds 2 }
        }
        if (Test-Path -LiteralPath $root) { Write-DeployLog ('Could not delete test server folder ' + $root) }
    }
}

# ---------------------------------------------------------------- status report for cloud sessions

# Lines worth reporting from a Paper log: ready lines, the Done line, self-test and errors.
function Get-ReportLines {
    param([string]$LogText, [int]$Max = 80)
    $out = @()
    foreach ($line in ($LogText -split "`r?`n")) {
        if ($line -match '_READY\b' -or $line -match 'Done \(' -or $line -match 'GEAR_SELFTEST' -or $line -cmatch '\b(ERROR|SEVERE)\b' -or $line -match 'Error occurred while enabling' -or $line -match "Could not load 'plugins") {
            if (Test-HarmlessLine $line) { continue }
            $l = $line.Trim()
            if ($l.Length -gt 300) { $l = $l.Substring(0, 300) + '...' }
            $out += $l
            if ($out.Count -ge $Max) { break }
        }
    }
    return ,$out
}

# Pushes DEPLOY_STATUS.md to the deploy-status branch (built with git plumbing in the staging clone,
# so no working tree is touched). Failure to push never fails the deploy.
function Publish-DeployStatus {
    param([string]$Result, [string]$Commit, [string]$PreviousCommit, [string]$Summary, [string[]]$Notes, [string]$LogText)
    try {
        $subject = ''
        if ($Commit) {
            $r = Invoke-Git -Repo $script:Ctx.Staging -Arguments @('log', '-1', '--format=%s', $Commit) -AllowFail
            if ($r.ExitCode -eq 0) { $subject = $r.Out.Trim() }
        }
        $sb = New-Object System.Text.StringBuilder
        [void]$sb.AppendLine('# JasperCraft deploy status')
        [void]$sb.AppendLine('')
        [void]$sb.AppendLine('Written by the owner''s one-click deployer (scripts/deploy). Newest result only; history is in this branch''s commits.')
        [void]$sb.AppendLine('')
        [void]$sb.AppendLine('- Result: **' + $Result + '**')
        [void]$sb.AppendLine('- Commit: `' + $Commit + '` ' + $subject)
        if ($PreviousCommit) { [void]$sb.AppendLine('- Previously live: `' + $PreviousCommit + '`') }
        [void]$sb.AppendLine('- Time: ' + (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm') + ' UTC')
        if ($Summary) { [void]$sb.AppendLine('- Changes: ' + $Summary) }
        if (@($Notes).Count -gt 0) {
            [void]$sb.AppendLine('')
            [void]$sb.AppendLine('## Notes')
            foreach ($n in $Notes) { if ($n) { [void]$sb.AppendLine('- ' + $n) } }
        }
        $lines = Get-ReportLines $LogText
        if ($lines.Count -gt 0) {
            [void]$sb.AppendLine('')
            [void]$sb.AppendLine('## Log lines (READY / Done / errors)')
            [void]$sb.AppendLine('')
            [void]$sb.AppendLine('```')
            foreach ($l in $lines) { [void]$sb.AppendLine($l) }
            [void]$sb.AppendLine('```')
        }
        $content = (Protect-DeployText $sb.ToString()) + "`n"
        $gitDir = Get-GitText $script:Ctx.Staging @('rev-parse', '--absolute-git-dir')
        $tmp = Join-Path $gitDir 'DEPLOY_STATUS.tmp'
        [IO.File]::WriteAllText($tmp, $content, $script:Utf8NoBom)
        $blob = Get-GitText $script:Ctx.Staging @('hash-object', '-w', $tmp)
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
        $tree = (Invoke-Git -Repo $script:Ctx.Staging -Arguments @('mktree') -InputText ("100644 blob " + $blob + "`tDEPLOY_STATUS.md`n")).Out.Trim()
        [void](Invoke-Git -Repo $script:Ctx.Staging -Arguments @('fetch', '--no-tags', 'origin', '+refs/heads/deploy-status:refs/remotes/origin/deploy-status') -AllowFail)
        $parentArgs = @()
        $pr = Invoke-Git -Repo $script:Ctx.Staging -Arguments @('rev-parse', '--verify', '-q', 'refs/remotes/origin/deploy-status') -AllowFail
        if ($pr.ExitCode -eq 0) { $parentArgs = @('-p', $pr.Out.Trim()) }
        $gitEnv = @{
            GIT_AUTHOR_NAME = 'JasperCraft deployer'; GIT_AUTHOR_EMAIL = 'deployer@jaspercraft.invalid'
            GIT_COMMITTER_NAME = 'JasperCraft deployer'; GIT_COMMITTER_EMAIL = 'deployer@jaspercraft.invalid'
        }
        $msg = 'Deploy status: ' + $Result + ' ' + $Commit
        $commitSha = (Invoke-Git -Repo $script:Ctx.Staging -Arguments (@('commit-tree', $tree) + $parentArgs + @('-m', $msg)) -Environment $gitEnv).Out.Trim()
        $push = Invoke-Git -Repo $script:Ctx.Staging -Arguments @('push', 'origin', ($commitSha + ':refs/heads/deploy-status')) -AllowFail
        if ($push.ExitCode -eq 0) { Say 'Status report uploaded to GitHub (deploy-status branch).' }
        else { Say 'Could not upload the status report to GitHub (not a problem for the game).' 'Warn' }
    } catch {
        Write-DeployLog ('Status report failed: ' + $_.Exception.Message)
        Say 'Could not upload the status report to GitHub (not a problem for the game).' 'Warn'
    }
}

# ---------------------------------------------------------------- live folder's git copy

# Makes GAME's git match the deployed commit without touching live jars or untracked/ignored files:
# fetch the commit from the staging clone, reset --mixed, then check out only the changed non-jar paths.
function Sync-GameGit {
    param([string]$Commit, [object[]]$Changes)
    $game = $script:Ctx.Game
    [void](Invoke-Git -Repo $script:Ctx.Staging -Arguments @('update-ref', 'refs/deploy/target', $Commit))
    if (-not (Test-GitCommitExists $game $Commit)) {
        [void](Invoke-Git -Repo $game -Arguments @('fetch', '--no-tags', $script:Ctx.Staging, '+refs/deploy/target:refs/deploy/live'))
    } else {
        [void](Invoke-Git -Repo $game -Arguments @('update-ref', 'refs/deploy/live', $Commit))
    }
    [void](Invoke-Git -Repo $game -Arguments @('reset', '--mixed', '-q', $Commit))
    $checkout = @()
    foreach ($c in @($Changes)) {
        $p = ConvertTo-RepoPath $c.Path
        if ($p.ToLowerInvariant().EndsWith('.jar')) { continue }
        if ($c.Status -eq 'D') {
            $local = Get-LocalPath $game $p
            if (Test-Path -LiteralPath $local -PathType Leaf) { Remove-FileSafely $local }
        } else {
            $checkout += $p
        }
    }
    $literal = @{ GIT_LITERAL_PATHSPECS = '1' }
    for ($i = 0; $i -lt $checkout.Count; $i += 40) {
        $chunk = @($checkout[$i..([Math]::Min($i + 39, $checkout.Count - 1))])
        [void](Invoke-Git -Repo $game -Arguments (@('checkout', '--') + $chunk) -Environment $literal)
    }
    $st = Invoke-Git -Repo $game -Arguments @('status', '--porcelain', '--untracked-files=no') -AllowFail
    if ($st.Out.Trim()) { Write-DeployLog ('Live folder git status after sync:' + "`n" + $st.Out.TrimEnd()) }
}

# Tracked files in GAME that differ from its HEAD (uncommitted edits), as repo paths.
function Get-GameModifiedPaths {
    $r = Invoke-Git -Repo $script:Ctx.Game -Arguments @('status', '--porcelain', '-z', '--untracked-files=no', '--no-renames') -AllowFail
    $paths = @()
    foreach ($entry in $r.Out.Split([char]0)) {
        if ($entry.Length -gt 3) { $paths += (ConvertTo-RepoPath $entry.Substring(3)) }
    }
    return ,$paths
}

function Test-GameTracked {
    param([string]$RepoPath)
    $r = Invoke-Git -Repo $script:Ctx.Game -Arguments @('ls-files', '--error-unmatch', '--', $RepoPath) -AllowFail -Environment @{ GIT_LITERAL_PATHSPECS = '1' }
    return ($r.ExitCode -eq 0)
}
