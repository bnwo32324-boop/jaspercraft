param()
$ErrorActionPreference = 'Continue'
Write-Output 'Eaglercraft 1.12.2 service status'
try { Invoke-RestMethod -Uri 'http://127.0.0.1:3308/healthz' -TimeoutSec 3 | ConvertTo-Json -Compress } catch { Write-Output 'site=offline' }
try { Invoke-RestMethod -Uri 'http://127.0.0.1:3310/status' -TimeoutSec 3 | ConvertTo-Json -Compress } catch { Write-Output 'gateway=offline' }
$watchdogStatus = Join-Path (Split-Path -Parent $PSScriptRoot) '.runtime\funnel-watchdog-status.json'
if (Test-Path -LiteralPath $watchdogStatus) { Write-Output ('funnelWatchdog=' + (Get-Content -Raw -LiteralPath $watchdogStatus).Trim()) } else { Write-Output 'funnelWatchdog=no-status-yet' }
foreach ($taskName in @('Eaglercraft Tailscale Web Server','Eaglercraft Game Wake Gateway','Eaglercraft Tailscale Funnel Watchdog')) {
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($task) { Write-Output "task=$taskName state=$($task.State)" }
}
Get-NetTCPConnection -State Listen -LocalPort 3308,3310,25565 -ErrorAction SilentlyContinue | Sort-Object LocalPort | Format-Table LocalAddress,LocalPort,OwningProcess -AutoSize
