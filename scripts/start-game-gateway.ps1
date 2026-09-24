param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'maintenance-mode') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'game-gateway-stop.request') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'gateway-supervisor-stop.request') -Force -ErrorAction SilentlyContinue
try {
    $status = Invoke-RestMethod -Uri 'http://127.0.0.1:3310/status' -TimeoutSec 3
    if ($status.alwaysOn -eq $true) { Write-Output 'The always-on 1.12.2 gateway is already running.'; exit 0 }
    throw 'Port 3310 is occupied by the legacy gateway.'
} catch {
    if ($_.Exception.Message -like '*legacy gateway*') { throw }
}
$task = Get-ScheduledTask -TaskName 'Eaglercraft Game Wake Gateway' -ErrorAction SilentlyContinue
if ($task) {
    Start-ScheduledTask -TaskName $task.TaskName
} else {
    $pythonw = 'C:\Users\AM\AppData\Local\Programs\Python\Python313\pythonw.exe'
    $supervisor = Join-Path $projectRoot 'server-tools\process_supervisor.py'
    Start-Process -FilePath $pythonw -ArgumentList ('-u "' + $supervisor + '" gateway') -WorkingDirectory $projectRoot -WindowStyle Hidden | Out-Null
}
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Milliseconds 500
    try {
        $status = Invoke-RestMethod -Uri 'http://127.0.0.1:3310/status' -TimeoutSec 3
        if ($status.alwaysOn -eq $true) { Write-Output 'Always-on 1.12.2 gateway is ready on local port 3310.'; exit 0 }
    } catch {}
}
throw 'The always-on gateway did not become healthy. Check .runtime\gateway-supervisor.log.'