param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
$pythonw = 'C:\Users\AM\AppData\Local\Programs\Python\Python313\pythonw.exe'
if (-not (Test-Path -LiteralPath $pythonw)) { throw "Windowless Python runtime not found: $pythonw" }
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'site-supervisor-stop.request') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'gateway-supervisor-stop.request') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'funnel-supervisor-stop.request') -Force -ErrorAction SilentlyContinue
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
$heartbeatTrigger = New-ScheduledTaskTrigger -Once -At ((Get-Date).AddMinutes(1)) -RepetitionInterval (New-TimeSpan -Minutes 1) -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Seconds 0)
$principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$supervisor = Join-Path $projectRoot 'server-tools\process_supervisor.py'
$definitions = @(
    @{ Name = 'Eaglercraft Tailscale Web Server'; Target = 'site'; Description = 'Supervises the persistent Eaglercraft 1.12.2 Tailscale site.' },
    @{ Name = 'Eaglercraft Game Wake Gateway'; Target = 'gateway'; Description = 'Supervises the always-on Eaglercraft 1.12.2 gateway and Paper server.' },
    @{ Name = 'Eaglercraft Tailscale Funnel Watchdog'; Target = 'funnel'; Description = 'Repairs only the Eaglercraft Funnel routes on ports 8443 and 10000 without touching Jaspergers.' }
)
foreach ($definition in $definitions) {
    $arguments = '-u "' + $supervisor + '" ' + $definition.Target
    $action = New-ScheduledTaskAction -Execute $pythonw -Argument $arguments -WorkingDirectory $projectRoot
    Register-ScheduledTask -TaskName $definition.Name -Action $action -Trigger @($logonTrigger, $heartbeatTrigger) -Settings $settings -Principal $principal -Description $definition.Description -Force | Out-Null
    Start-ScheduledTask -TaskName $definition.Name
    Write-Output "Installed and started supervised task: $($definition.Name)"
}
