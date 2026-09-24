param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'site-supervisor-stop.request') -Force -ErrorAction SilentlyContinue
$healthUrl = 'http://127.0.0.1:3308/healthz'
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
    if ($health.client -eq 'Eaglercraft 1.12.2') { Write-Output 'Eaglercraft 1.12.2 site is already running.'; exit 0 }
} catch {}
$task = Get-ScheduledTask -TaskName 'Eaglercraft Tailscale Web Server' -ErrorAction SilentlyContinue
if ($task) {
    Start-ScheduledTask -TaskName $task.TaskName
} else {
    $pythonw = 'C:\Users\AM\AppData\Local\Programs\Python\Python313\pythonw.exe'
    $supervisor = Join-Path $projectRoot 'server-tools\process_supervisor.py'
    Start-Process -FilePath $pythonw -ArgumentList ('-u "' + $supervisor + '" site') -WorkingDirectory $projectRoot -WindowStyle Hidden | Out-Null
}
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Milliseconds 500
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($health.client -eq 'Eaglercraft 1.12.2') { Write-Output 'Eaglercraft 1.12.2 site is ready on local port 3308.'; exit 0 }
    } catch {}
}
throw 'The Eaglercraft 1.12.2 site did not become healthy. Check .runtime\site-supervisor.log.'