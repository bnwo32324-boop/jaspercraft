# Tests for the one-click deployer's pure logic (scripts/deploy/DeployLogic.ps1) plus static checks
# that the deploy scripts stay Windows PowerShell 5.1 friendly.
# Run:  powershell -NoProfile -ExecutionPolicy Bypass -File tests\deploy-logic.test.ps1
#   or: pwsh -NoProfile -File tests/deploy-logic.test.ps1
# No Pester needed. Exit code 0 = all passed.

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$deployDir = Join-Path (Join-Path $repo 'scripts') 'deploy'
. (Join-Path $deployDir 'DeployLogic.ps1')

$script:Failures = 0
$script:Passes = 0
function Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) { $script:Passes++ } else { $script:Failures++; Write-Host ('FAIL: ' + $Name + ' ' + $Detail) -ForegroundColor Red }
}
function CheckEq {
    param([string]$Name, $Actual, $Expected)
    Check $Name ([string]$Actual -ceq [string]$Expected) ('expected [' + $Expected + '] got [' + $Actual + ']')
}

# ---------------------------------------------------------------- path classification
$kinds = @{
    'server/plugins/JasprGear.jar'                 = 'Plugin'
    'server/plugins/JasprHorrorBiomes.jar'         = 'Plugin'
    'server/plugins/TestServerControl.jar'         = 'Plugin'
    'server\plugins\JasprRevive.jar'               = 'Plugin'
    'server/plugins/AuthMe.jar'                    = 'Other'
    'server/plugins/update/JasprGear.jar'          = 'Other'
    'server/plugins/JasprGear/config.yml'          = 'Other'
    'server/plugins/JasprGear.txt'                 = 'Other'
    'server/custom-plugins/JasprGear/src/chat/jaspr/gear/GearPlugin.java' = 'Other'
    'site/classes.js'                              = 'Site'
    'site/client.html'                             = 'Site'
    'site/lang/en_US.lang'                         = 'Site'
    'site'                                         = 'Other'
    'sitemap.xml'                                  = 'Other'
    'scripts/deploy/Deploy-JasperCraft.ps1'        = 'Review'
    'scripts/deploy/DeployLogic.ps1'               = 'Review'
    'Scripts/Deploy/new-helper.ps1'                = 'Review'
    'Deploy JasperCraft.bat'                       = 'Review'
    'Undo last deploy.bat'                         = 'Review'
    'scripts/stop-game-server.ps1'                 = 'Other'
    'scripts/deployment-notes.md'                  = 'Other'
    'CLAUDE.md'                                    = 'Other'
    'candidate/structure-audit/testserver-template/paper.yml' = 'Other'
}
foreach ($k in $kinds.Keys) { CheckEq ('kind of ' + $k) (Get-DeployPathKind $k) $kinds[$k] }

CheckEq 'plugin name' (Get-PluginNameFromPath 'server/plugins/JasprGear.jar') 'JasprGear'
CheckEq 'plugin name backslash' (Get-PluginNameFromPath 'server\plugins\TestServerControl.jar') 'TestServerControl'

# ---------------------------------------------------------------- name-status parsing and plan
$z = [char]0
$diff = 'M' + $z + 'server/plugins/JasprGear.jar' + $z + 'A' + $z + 'site/new file.js' + $z + 'D' + $z + 'site/old.css' + $z +
        'M' + $z + 'CLAUDE.md' + $z + 'M' + $z + 'scripts/deploy/DeployLogic.ps1' + $z + 'A' + $z + 'server/plugins/TestServerControl.jar' + $z
$changes = ConvertFrom-NameStatusZ $diff
CheckEq 'parsed count' $changes.Count 6
CheckEq 'parsed path with space' $changes[1].Path 'site/new file.js'
CheckEq 'parsed status' $changes[2].Status 'D'
$renamed = ConvertFrom-NameStatusZ ('R100' + $z + 'site/a.js' + $z + 'site/b.js' + $z)
CheckEq 'rename -> delete+add count' $renamed.Count 2
CheckEq 'rename old deleted' ($renamed[0].Status + ' ' + $renamed[0].Path) 'D site/a.js'
CheckEq 'rename new added' ($renamed[1].Status + ' ' + $renamed[1].Path) 'A site/b.js'
CheckEq 'empty diff' (ConvertFrom-NameStatusZ '').Count 0

$plan = Get-DeployPlan $changes
CheckEq 'plan plugins' $plan.Plugins.Count 2
CheckEq 'plan changed plugin names' ($plan.ChangedPlugins -join ',') 'JasprGear,TestServerControl'
CheckEq 'plan site' $plan.Site.Count 2
CheckEq 'plan other' $plan.Other.Count 1
CheckEq 'plan review' $plan.Review.Count 1
Check 'plan needs restart' $plan.NeedsRestart
Check 'plan deployable' $plan.Deployable

$sitePlan = Get-DeployPlan (ConvertFrom-NameStatusZ ('M' + $z + 'site/classes.js' + $z + 'M' + $z + 'site/client.html' + $z))
Check 'site-only: no restart' (-not $sitePlan.NeedsRestart)
Check 'site-only: deployable' $sitePlan.Deployable

$docPlan = Get-DeployPlan (ConvertFrom-NameStatusZ ('M' + $z + 'GEAR_UPDATE.md' + $z))
Check 'docs-only: not deployable' (-not $docPlan.Deployable)

$delPlan = Get-DeployPlan (ConvertFrom-NameStatusZ ('D' + $z + 'server/plugins/JasprBlight.jar' + $z))
CheckEq 'deleted plugin is unsupported' $delPlan.Unsupported.Count 1
CheckEq 'deleted plugin not deployed' $delPlan.Plugins.Count 0

# ---------------------------------------------------------------- ready tokens
$live = Get-RequiredReadyTokens -Plugins @('JasprHorrorBiomes', 'JasprApocalypse', 'JasprInvasions') -Context Live
Check 'live tokens horror' ($live -contains 'HORROR_BIOMES_READY' -and $live -contains 'STRUCTURES_READY')
Check 'live tokens apocalypse' ($live -contains 'APOCALYPSE_READY')
Check 'live tokens generic' ($live -contains 'Enabling JasprInvasions v')
$test = Get-RequiredReadyTokens -Plugins @('JasprApocalypse', 'TestServerControl', 'JasprVoiceChat', 'JasprGear', 'JasprImportedWorldgen') -Context Test
CheckEq 'test tokens skip expected failures and excluded' ($test -join ',') 'GEAR_READY,Enabling JasprImportedWorldgen v'
CheckEq 'no plugins -> no tokens' (Get-RequiredReadyTokens -Plugins @() -Context Live).Count 0
$missing = Get-MissingTokens -LogText "[INFO] GEAR_READY items=9`n[INFO] Done (12.3s)!" -Tokens @('GEAR_READY', 'HORROR_BIOMES_READY')
CheckEq 'missing tokens' ($missing -join ',') 'HORROR_BIOMES_READY'

# ---------------------------------------------------------------- log problem detection
$log = @(
    '[10:00:01] [Server thread/INFO]: [JasprGear] Enabling JasprGear v2.0.0',
    '[10:00:02] [Server thread/INFO]: [JasprGear] GEAR_READY items=12 errors=0',
    '[10:00:03] [Server thread/ERROR]: [EaglerXServer] ForceAliveListener failed to bind',
    '[10:00:04] [Server thread/WARN]: [AuthMe] Could not download GeoLite database',
    "[10:00:05] [Server thread/ERROR]: Could not load 'plugins/JasprApocalypse.jar' in folder 'plugins'",
    'org.bukkit.plugin.UnknownDependencyException: AuthMe',
    '	at org.bukkit.plugin.SimplePluginManager.loadPlugins(SimplePluginManager.java:216)',
    '[10:00:06] [Server thread/INFO]: Done (8.1s)! For help, type "help" or "?"'
)
CheckEq 'known harmless + expected apocalypse failure -> no problems' (Find-JasprProblems -Lines $log -IgnorePlugins @('JasprApocalypse')).Count 0
$bad = $log + @(
    '[10:00:07] [Server thread/ERROR]: Error occurred while enabling JasprRevive v1.1 (Is it up to date?)',
    'java.lang.NullPointerException',
    '	at chat.jaspr.revive.RevivePlugin.onEnable(RevivePlugin.java:50)'
)
$problems = Find-JasprProblems -Lines $bad -IgnorePlugins @('JasprApocalypse')
CheckEq 'jaspr enable error found' $problems.Count 1
Check 'jaspr enable error text' ($problems[0] -like '*JasprRevive*')
$trace = @(
    '[10:00:08] [Server thread/WARN]: [JasprBlight] Task #5 generated an exception',
    'java.lang.IllegalStateException: boom',
    '	at chat.jaspr.blight.BlightTask.run(BlightTask.java:20)'
)
CheckEq 'exception with jaspr stack found' (Find-JasprProblems -Lines $trace -IgnorePlugins @()).Count 1
$foreign = @(
    '[10:00:09] [Server thread/ERROR]: [ViaVersion] something broke',
    'java.lang.RuntimeException: nope',
    '	at us.myles.via.Thing.run(Thing.java:1)'
)
CheckEq 'non-jaspr error ignored' (Find-JasprProblems -Lines $foreign -IgnorePlugins @()).Count 0
CheckEq 'lowercase error word is not an error' (Find-JasprProblems -Lines @('[INFO] [JasprGear] no error here, errors=0') -IgnorePlugins @()).Count 0

$loadFail = Find-PluginLoadFailures -Lines $bad -Plugins @('JasprRevive', 'JasprApocalypse', 'JasprGear')
CheckEq 'load failures found' $loadFail.Count 2
CheckEq 'no prefix match on plugin names' (Find-PluginLoadFailures -Lines @('Error occurred while enabling JasprGearExtra v1 (x)') -Plugins @('JasprGear')).Count 0

# ---------------------------------------------------------------- redaction
$raw = @(
    '[10:00:01] [Server thread/INFO]: Steve[/192.168.1.23:51234] logged in with entity id 5',
    '[10:00:02] [Server thread/INFO]: tailscale peer 100.101.102.103 connected',
    '[10:00:03] [Server thread/INFO]: v6 client [2001:db8:85a3::8a2e:370:7334]:25565 joined',
    '[10:00:04] [Server thread/INFO]: loopback ::1 ok',
    '[10:00:05] [Server thread/INFO]: [AuthMe] Player Bob password changed',
    'token=abcdef123456',
    'read C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\private\owner-bootstrap.json',
    'loaded C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\.env',
    'Paper version git-Paper-1618 (MC: 1.12.2) at 12:34:56',
    'STRUCTURES_READY version=7 designs=140 terrainEpoch=3',
    'from C:\Users\AM\Documents\x.log'
) -join "`n"
$red = Protect-DeployText $raw
Check 'ipv4 with port removed' ($red -notmatch '192\.168\.1\.23' -and $red -notmatch '51234')
Check 'tailscale ip removed' ($red -notmatch '100\.101\.102\.103')
Check 'ipv6 removed' ($red -notmatch '2001:db8' -and $red -notmatch '8a2e')
Check 'loopback v6 removed' ($red -notmatch '::1')
Check 'password line removed' ($red -notmatch 'Bob')
Check 'token line removed' ($red -notmatch 'abcdef123456')
Check 'private path line removed' ($red -notmatch 'owner-bootstrap')
Check '.env line removed' ($red -notmatch '\.env')
Check 'times kept' ($red -match '12:34:56' -and $red -match '\[10:00:01\]')
Check 'version kept' ($red -match '1\.12\.2')
Check 'ready line kept' ($red -match 'STRUCTURES_READY version=7')
Check 'user folder masked' ($red -notmatch 'Users\\AM' -and $red -match '%USERPROFILE%')
Check 'player name kept' ($red -match 'Steve\[/\[ip\]\]')
CheckEq 'empty redaction' (Protect-DeployText '') ''

# ---------------------------------------------------------------- command-line quoting
CheckEq 'quote plain' (ConvertTo-QuotedArgument 'fetch') 'fetch'
CheckEq 'quote space' (ConvertTo-QuotedArgument 'C:\Program Files\x') '"C:\Program Files\x"'
CheckEq 'quote empty' (ConvertTo-QuotedArgument '') '""'
CheckEq 'quote embedded quote' (ConvertTo-QuotedArgument 'a"b') '"a\"b"'
CheckEq 'quote trailing backslash' (ConvertTo-QuotedArgument 'C:\dir with space\') '"C:\dir with space\\"'
CheckEq 'join' (Join-CommandLine @('-C', 'C:\a b', 'log', '--format=%h  %s')) '-C "C:\a b" log "--format=%h  %s"'

# ---------------------------------------------------------------- restart gate
$now = [datetime]'2026-09-24T12:00:00'
$gap = [TimeSpan]::FromMinutes(5)
CheckEq 'gate: status down' (Get-RestartWaitReason -Status $null -LastRestart $null -Now $now -MinGap $gap) 'the server status page is not answering'
$st0 = New-Object PSObject -Property @{ server = 'running'; activeConnections = 0 }
$st2 = New-Object PSObject -Property @{ server = 'running'; activeConnections = 2 }
$st1 = New-Object PSObject -Property @{ server = 'running'; activeConnections = 1 }
Check 'gate: clear' ($null -eq (Get-RestartWaitReason -Status $st0 -LastRestart $now.AddMinutes(-6) -Now $now -MinGap $gap))
Check 'gate: clear without restart info' ($null -eq (Get-RestartWaitReason -Status $st0 -LastRestart $null -Now $now -MinGap $gap))
CheckEq 'gate: players' (Get-RestartWaitReason -Status $st2 -LastRestart $null -Now $now -MinGap $gap) '2 players are online'
CheckEq 'gate: one player' (Get-RestartWaitReason -Status $st1 -LastRestart $null -Now $now -MinGap $gap) '1 player is online'
Check 'gate: recent restart' ((Get-RestartWaitReason -Status $st0 -LastRestart $now.AddMinutes(-2) -Now $now -MinGap $gap) -like 'the server restarted 2m 00s ago*')
CheckEq 'duration' (Format-Duration ([TimeSpan]::FromSeconds(125))) '2m 05s'

# ---------------------------------------------------------------- static checks on the deploy scripts
$files = @(Get-ChildItem -LiteralPath $deployDir -Filter '*.ps1' -File) + @(Get-Item -LiteralPath (Join-Path $repo 'Deploy JasperCraft.bat'), (Join-Path $repo 'Undo last deploy.bat'))
foreach ($f in $files) {
    $bytes = [IO.File]::ReadAllBytes($f.FullName)
    $nonAscii = @($bytes | Where-Object { $_ -gt 127 }).Count
    CheckEq ('pure ASCII: ' + $f.Name) $nonAscii 0
    $text = [IO.File]::ReadAllText($f.FullName)
    Check ('never writes maintenance-mode: ' + $f.Name) ($text -notmatch "maintenance-mode'" -and $text -notmatch 'stop-game-server')
    Check ('no scheduled task or service: ' + $f.Name) ($text -notmatch 'Register-ScheduledTask|schtasks|New-Service|sc\.exe')
    if ($f.Extension -eq '.bat') {
        Check ('CRLF line endings: ' + $f.Name) (($text -split "`r`n").Count -eq ($text -split "`n").Count)
        Check ('bat uses -NoProfile -ExecutionPolicy Bypass -File: ' + $f.Name) ($text -match 'powershell(\.exe)? -NoProfile -ExecutionPolicy Bypass -File')
        continue
    }
    $tokens = $null
    $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($f.FullName, [ref]$tokens, [ref]$errors)
    CheckEq ('parses: ' + $f.Name) @($errors).Count 0
    foreach ($e in @($errors)) { Write-Host ('   ' + $e.Extent.StartLineNumber + ': ' + $e.Message) }
    # Syntax and parameters that Windows PowerShell 5.1 does not have.
    $ps7Tokens = @($tokens | Where-Object { @('AndAnd', 'OrOr', 'QuestionQuestion', 'QuestionQuestionEquals', 'QuestionMark', 'QuestionDot', 'QuestionLBracket') -contains [string]$_.Kind })
    Check ('no PS7-only operators (&& || ?? ?. ternary) in ' + $f.Name) ($ps7Tokens.Count -eq 0) (($ps7Tokens | ForEach-Object { [string]$_.Extent.StartLineNumber }) -join ',')
    foreach ($pattern in @('-SkipHttpErrorCheck', '-AsHashtable', '-Encoding\s+utf8NoBOM', '-AsByteStream', 'ForEach-Object\s+-Parallel', '\$PSStyle', '-AdditionalChildPath')) {
        Check ('no PS7-only feature ' + $pattern + ' in ' + $f.Name) ($text -notmatch $pattern)
    }
    $joins = @($ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Join-Path' }, $true))
    $wide = @($joins | Where-Object { @($_.CommandElements | Where-Object { $_ -isnot [System.Management.Automation.Language.CommandParameterAst] }).Count -gt 3 })
    Check ('Join-Path takes 2 arguments on 5.1: ' + $f.Name) ($wide.Count -eq 0) (($wide | ForEach-Object { [string]$_.Extent.StartLineNumber }) -join ',')
    # Invoke-WebRequest must use -UseBasicParsing on 5.1 (otherwise it needs Internet Explorer).
    foreach ($line in ($text -split "`n")) {
        if ($line -match 'Invoke-(WebRequest|RestMethod) ' -and $line -notmatch '^\s*#') {
            Check ('-UseBasicParsing in ' + $f.Name + ': ' + $line.Trim()) ($line -match '-UseBasicParsing')
        }
    }
}

Write-Host ('deploy-logic tests: ' + $script:Passes + ' passed, ' + $script:Failures + ' failed')
if ($script:Failures -gt 0) { exit 1 }
exit 0
