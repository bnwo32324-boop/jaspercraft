param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'site-supervisor-stop.request'), "stop`r`n")
$task = Get-ScheduledTask -TaskName 'Eaglercraft Tailscale Web Server' -ErrorAction SilentlyContinue
if ($task -and $task.State -eq 'Running') { Stop-ScheduledTask -TaskName $task.TaskName }
$pidFile = Join-Path $runtimeRoot 'http-server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $sitePid = 0
    if ([int]::TryParse(([IO.File]::ReadAllText($pidFile)).Trim(), [ref]$sitePid)) {
        $info = Get-CimInstance Win32_Process -Filter "ProcessId=$sitePid" -ErrorAction SilentlyContinue
        if ($info -and $info.Name -match '^python(w)?\.exe$' -and $info.CommandLine -like '*site_server.py*') { Stop-Process -Id $sitePid -Force }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}
Write-Output 'Stopped the supervised Eaglercraft 1.12.2 local site.'