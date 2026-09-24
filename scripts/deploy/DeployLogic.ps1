# JasperCraft deployer: pure logic (no files, no network, no processes).
# Dot-sourced by Deploy-JasperCraft.ps1, Undo-LastDeploy.ps1 and tests/deploy-logic.test.ps1.
# Must stay Windows PowerShell 5.1 compatible and pure ASCII (5.1 reads BOM-less files as ANSI).

# Plugins that never run on the Gate 1 test server (they need AuthMe / EaglerXServer / real players).
$script:TestServerExcludedPlugins = @('JasprVoiceChat', 'JasprWorldReset', 'TestServerControl')

# Plugins that are loaded on the test server but are expected to fail there (JasprApocalypse needs AuthMe).
$script:TestServerExpectedFailures = @('JasprApocalypse')

# Plugins whose ready line depends on live config (the fresh test server leaves them disabled):
# on the test server they are only checked with the generic "Enabling <name>" line.
$script:TestServerGenericOnly = @('JasprImportedWorldgen')

# Startup lines each plugin prints when it is ready. Plugins not listed are checked with the generic
# "Enabling <name>" line plus the absence of "Error occurred while enabling <name>".
$script:PluginReadyTokens = @{
    'JasprApocalypse'       = @('APOCALYPSE_READY')
    'JasprBlight'           = @('BLIGHT_READY')
    'JasprDaylight'         = @('DAYLIGHT_READY')
    'JasprGear'             = @('GEAR_READY')
    'JasprGraves'           = @('GRAVES_READY')
    'JasprHorrorBiomes'     = @('HORROR_BIOMES_READY', 'STRUCTURES_READY')
    'JasprImportedWorldgen' = @('IMPORTED_ASSETS_READY')
    'JasprRevive'           = @('REVIVE_READY')
}

# Known harmless log lines (never count as problems).
$script:HarmlessLogPatterns = @(
    'ForceAliveListener',
    'GeoLite',
    'GeoIp'
)

function ConvertTo-RepoPath {
    param([string]$Path)
    $p = $Path -replace '\\', '/'
    while ($p.StartsWith('./')) { $p = $p.Substring(2) }
    return $p.TrimStart('/')
}

# Kind of a changed repository path:
#   Review - the deployer itself; the owner must not deploy it without a Claude review
#   Plugin - a live plugin jar (server/plugins/Jaspr*.jar or TestServerControl.jar)
#   Site   - a website file (site/**), served live straight from GAME\site
#   Other  - everything else: brought into the live folder's git copy, not restarted
function Get-DeployPathKind {
    param([Parameter(Mandatory = $true)][string]$Path)
    $p = ConvertTo-RepoPath $Path
    $lower = $p.ToLowerInvariant()
    if ($lower.StartsWith('scripts/deploy/')) { return 'Review' }
    if ($lower -eq 'deploy jaspercraft.bat' -or $lower -eq 'undo last deploy.bat') { return 'Review' }
    if ($lower -match '^server/plugins/[^/]+\.jar$') {
        $name = $p.Substring('server/plugins/'.Length)
        if ($name -like 'Jaspr*.jar' -or $name -ieq 'TestServerControl.jar') { return 'Plugin' }
        return 'Other'
    }
    if ($lower.StartsWith('site/') -and $lower.Length -gt 5) { return 'Site' }
    return 'Other'
}

function Get-PluginNameFromPath {
    param([string]$Path)
    $leaf = (ConvertTo-RepoPath $Path).Split('/')[-1]
    if ($leaf.ToLowerInvariant().EndsWith('.jar')) { return $leaf.Substring(0, $leaf.Length - 4) }
    return $leaf
}

# Parses "git diff --name-status -z --no-renames" output into objects {Status; Path}.
function ConvertFrom-NameStatusZ {
    param([string]$Text)
    $result = @()
    if ([string]::IsNullOrEmpty($Text)) { return ,$result }
    $parts = $Text.Split([char]0)
    $i = 0
    while ($i -lt $parts.Length) {
        $status = $parts[$i]
        if ([string]::IsNullOrEmpty($status)) { $i++; continue }
        $code = $status.Substring(0, 1)
        if ($code -eq 'R' -or $code -eq 'C') {
            # Only happens without --no-renames: old path, new path.
            $result += New-Object PSObject -Property @{ Status = 'D'; Path = $parts[$i + 1] }
            $result += New-Object PSObject -Property @{ Status = 'A'; Path = $parts[$i + 2] }
            $i += 3
            continue
        }
        $result += New-Object PSObject -Property @{ Status = $code; Path = $parts[$i + 1] }
        $i += 2
    }
    return ,$result
}

# Builds the deploy plan from a list of {Status; Path}.
function Get-DeployPlan {
    param([object[]]$Changes)
    $plan = New-Object PSObject -Property @{
        Review        = @()
        Plugins       = @()
        Site          = @()
        Other         = @()
        Unsupported   = @()
        NeedsRestart  = $false
        Deployable    = $false
        ChangedPlugins = @()
    }
    foreach ($c in @($Changes)) {
        if ($null -eq $c) { continue }
        $path = ConvertTo-RepoPath $c.Path
        $entry = New-Object PSObject -Property @{ Status = $c.Status; Path = $path; Kind = (Get-DeployPathKind $path) }
        switch ($entry.Kind) {
            'Review' { $plan.Review += $entry }
            'Plugin' {
                if ($entry.Status -eq 'D') { $plan.Unsupported += $entry } else { $plan.Plugins += $entry }
            }
            'Site' { $plan.Site += $entry }
            default { $plan.Other += $entry }
        }
    }
    $plan.ChangedPlugins = @($plan.Plugins | ForEach-Object { Get-PluginNameFromPath $_.Path })
    $plan.NeedsRestart = ($plan.Plugins.Count -gt 0)
    $plan.Deployable = ($plan.Plugins.Count -gt 0 -or $plan.Site.Count -gt 0)
    return $plan
}

# Lines that must appear in the log for the given plugins to count as started.
# Context 'Test' skips plugins that cannot start on the test server.
function Get-RequiredReadyTokens {
    param([string[]]$Plugins, [ValidateSet('Test', 'Live')][string]$Context = 'Live')
    $tokens = @()
    foreach ($name in @($Plugins)) {
        if ([string]::IsNullOrEmpty($name)) { continue }
        if ($Context -eq 'Test') {
            if ($script:TestServerExcludedPlugins -contains $name) { continue }
            if ($script:TestServerExpectedFailures -contains $name) { continue }
        }
        $generic = ($Context -eq 'Test' -and $script:TestServerGenericOnly -contains $name)
        if ($script:PluginReadyTokens.ContainsKey($name) -and -not $generic) {
            $tokens += $script:PluginReadyTokens[$name]
        } else {
            $tokens += ('Enabling ' + $name + ' v')
        }
    }
    return ,@($tokens | Select-Object -Unique)
}

# Returns the required tokens that are not in the log text yet.
function Get-MissingTokens {
    param([string]$LogText, [string[]]$Tokens)
    $missing = @()
    foreach ($t in @($Tokens)) {
        if ([string]::IsNullOrEmpty($t)) { continue }
        if ($LogText.IndexOf($t, [StringComparison]::Ordinal) -lt 0) { $missing += $t }
    }
    return ,$missing
}

function Test-HarmlessLine {
    param([string]$Line, [string[]]$ExtraPatterns)
    foreach ($p in @($script:HarmlessLogPatterns) + @($ExtraPatterns)) {
        if ([string]::IsNullOrEmpty($p)) { continue }
        if ($Line.IndexOf($p, [StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
    }
    return $false
}

# Finds log problems caused by Jaspr plugins: ERROR/SEVERE lines or exceptions whose line or stack
# trace (the following indented "at ..." / "Caused by" lines) mentions a Jaspr plugin.
# Plugins listed in -IgnorePlugins (expected failures) are skipped. Returns the offending first lines.
function Find-JasprProblems {
    param([string[]]$Lines, [string[]]$IgnorePlugins, [string[]]$ExtraHarmless)
    $problems = @()
    $all = @($Lines)
    for ($i = 0; $i -lt $all.Length; $i++) {
        $line = [string]$all[$i]
        $isError = ($line -cmatch '\b(ERROR|SEVERE)\b')
        $isException = (($line -cmatch '\b[a-z][\w$]*(\.[\w$]+)*\.[A-Z][\w$]*(Exception|Error)\b') -or ($line -cmatch 'Exception in thread'))
        if (-not ($isError -or $isException)) { continue }
        if ($line -match '^\s+at\s' -or $line -match '^\s*Caused by') { continue }
        # Gather this line plus its stack trace.
        $block = New-Object System.Collections.ArrayList
        [void]$block.Add($line)
        $j = $i + 1
        while ($j -lt $all.Length -and $j -le $i + 60) {
            $next = [string]$all[$j]
            if ($next -match '^\s+at\s' -or $next -match '^\s*Caused by' -or $next -match '^\s+\.\.\. \d+ more' -or $next -match '^[A-Za-z_$][\w$]*(\.[\w$]+)+(Exception|Error)') {
                [void]$block.Add($next); $j++
            } else { break }
        }
        $text = ($block -join "`n")
        $i = $j - 1
        if (Test-HarmlessLine -Line $text -ExtraPatterns $ExtraHarmless) { continue }
        if ($text -notmatch 'Jaspr' -and $text -notmatch 'chat\.jaspr\.') { continue }
        $ignored = $false
        foreach ($name in @($IgnorePlugins)) {
            if ([string]::IsNullOrEmpty($name)) { continue }
            if ($line.IndexOf($name, [StringComparison]::OrdinalIgnoreCase) -ge 0) { $ignored = $true; break }
        }
        if ($ignored) { continue }
        $problems += $line.Trim()
    }
    return ,$problems
}

# Lines that show a changed plugin failed to load or enable (Paper's own wording).
function Find-PluginLoadFailures {
    param([string[]]$Lines, [string[]]$Plugins)
    $found = @()
    foreach ($line in @($Lines)) {
        foreach ($name in @($Plugins)) {
            if ([string]::IsNullOrEmpty($name)) { continue }
            $enableFailed = ($line.IndexOf('Error occurred while enabling ' + $name + ' ', [StringComparison]::Ordinal) -ge 0)
            $loadFailed = (($line.IndexOf("Could not load 'plugins", [StringComparison]::Ordinal) -ge 0) -and ($line.IndexOf($name + '.jar', [StringComparison]::Ordinal) -ge 0))
            if ($enableFailed -or $loadFailed) {
                $found += ([string]$line).Trim()
            }
        }
    }
    return ,@($found | Select-Object -Unique)
}

$script:Ipv6Evaluator = [System.Text.RegularExpressions.MatchEvaluator] {
    param($m)
    $v = $m.Value
    if ($v.Contains('::') -or $v -match '[a-fA-F]' -or ($v.Split(':').Length -ge 5)) { return '[ip]' }
    return $v
}

# Removes anything private from text before it leaves the PC (DEPLOY_STATUS.md on GitHub):
# drops lines that mention secrets or private files, masks IPv4/IPv6 addresses and the Windows user folder.
function Protect-DeployText {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    $dropPattern = '(?i)(password|passwd|token|secret|api[_-]?key|credential|bearer|authorization|cookie|session[_-]?id|ticket|private[\\/]|\.env\b|owner-bootstrap)'
    $out = New-Object System.Collections.ArrayList
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -match $dropPattern) { [void]$out.Add('[line removed: private]'); continue }
        $l = $line
        # IPv4 (optionally with :port)
        $l = [regex]::Replace($l, '(?<![\d.])(\d{1,3}\.){3}\d{1,3}(?![\d.])(:\d{1,5})?', '[ip]')
        # IPv6: colon-separated hex groups; keeps clock times like 12:34:56 (no letters, no '::', < 5 groups)
        $l = [regex]::Replace($l, '(?i)(?<![\w:.])[0-9a-f]{0,4}(:[0-9a-f]{0,4}){2,7}(?![\w:])', $script:Ipv6Evaluator)
        $l = [regex]::Replace($l, '(?i)[A-Z]:\\Users\\[^\\\s]+', '%USERPROFILE%')
        [void]$out.Add($l)
    }
    return ($out -join "`n")
}

# Windows command-line quoting for one argument (CommandLineToArgvW rules), for .NET Framework's
# ProcessStartInfo.Arguments (it has no ArgumentList on Windows PowerShell 5.1).
function ConvertTo-QuotedArgument {
    param([AllowEmptyString()][string]$Argument)
    if ($null -eq $Argument) { $Argument = '' }
    if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') { return $Argument }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('"')
    $backslashes = 0
    foreach ($ch in $Argument.ToCharArray()) {
        if ($ch -eq '\') { $backslashes++; continue }
        if ($ch -eq '"') {
            [void]$sb.Append('\' * ($backslashes * 2 + 1)); [void]$sb.Append('"')
        } else {
            if ($backslashes -gt 0) { [void]$sb.Append('\' * $backslashes) }
            [void]$sb.Append($ch)
        }
        $backslashes = 0
    }
    if ($backslashes -gt 0) { [void]$sb.Append('\' * ($backslashes * 2)) }
    [void]$sb.Append('"')
    return $sb.ToString()
}

function Join-CommandLine {
    param([string[]]$Arguments)
    return (@($Arguments) | ForEach-Object { ConvertTo-QuotedArgument $_ }) -join ' '
}

# Human wording for a duration.
function Format-Duration {
    param([TimeSpan]$Span)
    if ($Span.TotalSeconds -lt 0) { $Span = [TimeSpan]::Zero }
    $m = [int][Math]::Floor($Span.TotalMinutes)
    $s = $Span.Seconds
    if ($m -gt 0) { return ('{0}m {1:00}s' -f $m, $s) }
    return ('{0}s' -f $s)
}

# Whether a restart has to wait: players online, or the server restarted less than MinGap ago.
# Returns $null when it is fine to restart now, else a plain-English reason.
function Get-RestartWaitReason {
    param($Status, [Nullable[datetime]]$LastRestart, [datetime]$Now, [TimeSpan]$MinGap)
    if ($null -eq $Status) { return 'the server status page is not answering' }
    $players = 0
    if ($null -ne $Status.activeConnections) { $players = [int]$Status.activeConnections }
    if ($players -gt 0) {
        if ($players -eq 1) { return '1 player is online' }
        return ([string]$players + ' players are online')
    }
    if ($null -ne $LastRestart) {
        $since = $Now - [datetime]$LastRestart
        if ($since -lt $MinGap) {
            return ('the server restarted ' + (Format-Duration $since) + ' ago (waiting until it has run for ' + (Format-Duration $MinGap) + ')')
        }
    }
    return $null
}
