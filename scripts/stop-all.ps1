param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'gateway-supervisor-stop.request'), "stop`r`n")
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'site-supervisor-stop.request'), "stop`r`n")
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'funnel-supervisor-stop.request'), "stop`r`n")
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'game-gateway-stop.request'), "stop`r`n")
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    Start-Sleep -Seconds 1
    if (-not (Get-NetTCPConnection -State Listen -LocalPort 3310,25565 -ErrorAction SilentlyContinue)) { break }
}
foreach ($taskName in @('Eaglercraft Tailscale Funnel Watchdog','Eaglercraft Game Wake Gateway','Eaglercraft Tailscale Web Server')) {
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($task -and $task.State -eq 'Running') { Stop-ScheduledTask -TaskName $taskName }
}
& (Join-Path $PSScriptRoot 'stop-site.ps1')
Write-Output 'Stopped the supervised 1.12.2 site, gateway, Funnel watchdog, and Paper server.'
