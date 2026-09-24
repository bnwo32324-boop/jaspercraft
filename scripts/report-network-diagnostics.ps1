param(
    [string]$Player = "",
    [ValidateRange(1, 720)][int]$Hours = 24,
    [string]$JasprRoot = "C:\Users\AM\Desktop\Curser Test\Jaspergers"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$since = [DateTimeOffset]::UtcNow.AddHours(-$Hours)

function Read-JsonLines([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $share = [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, $share)
    $reader = [IO.StreamReader]::new($stream)
    try {
        while (($line = $reader.ReadLine()) -ne $null) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            try {
                $record = $line | ConvertFrom-Json
                $at = [DateTimeOffset]::Parse([string]$record.at)
                if ($at -ge $since) { $record }
            } catch { }
        }
    } finally {
        $reader.Dispose()
    }
}

function Get-Percentile([double[]]$Values, [double]$Percentile) {
    if (-not $Values -or $Values.Count -eq 0) { return 0 }
    $ordered = @($Values | Sort-Object)
    $index = [Math]::Min($ordered.Count - 1, [Math]::Max(0, [Math]::Ceiling($ordered.Count * $Percentile) - 1))
    return [Math]::Round($ordered[$index], 1)
}

$networkLog = Join-Path $projectRoot "server\plugins\TestServerControl\network-diagnostics.jsonl"
$networkRecords = @(Read-JsonLines $networkLog | Where-Object {
    -not $Player -or [string]$_.player -ieq $Player
})

$gatewayRecords = @()
$sessionRoot = Join-Path $JasprRoot "logs\sessions"
if (Test-Path -LiteralPath $sessionRoot -PathType Container) {
    foreach ($session in Get-ChildItem -LiteralPath $sessionRoot -Directory | Where-Object LastWriteTimeUtc -GE $since.UtcDateTime) {
        $events = Join-Path $session.FullName "events.jsonl"
        $gatewayRecords += @(Read-JsonLines $events | Where-Object {
            [string]$_.event -like "jaspercraft.proxy.*" -and (-not $Player -or [string]$_.gameName -ieq $Player)
        })
    }
}

$samples = @($networkRecords | Where-Object event -EQ "network.sample")
$closed = @($gatewayRecords | Where-Object event -EQ "jaspercraft.proxy.closed")
$alerts = @($networkRecords | Where-Object event -In @("network.high_ping", "network.ping_recovered"))

Write-Output "JasperCraft network diagnostics since $($since.ToString('u'))"
if ($Player) { Write-Output "Player filter: $Player" }

if ($samples.Count) {
    Write-Output "`nServer-authoritative ping and tick health"
    $samples | Group-Object player | ForEach-Object {
        $rows = @($_.Group)
        $pings = @($rows | ForEach-Object { [double]$_.pingMs } | Where-Object { $_ -ge 0 })
        [pscustomobject]@{
            Player = $_.Name
            Samples = $rows.Count
            MeanPingMs = if ($pings.Count) { [Math]::Round(($pings | Measure-Object -Average).Average, 1) } else { -1 }
            P95PingMs = Get-Percentile $pings 0.95
            MaxPingMs = if ($pings.Count) { ($pings | Measure-Object -Maximum).Maximum } else { -1 }
            MaxTickP95Ms = ($rows.tickP95Ms | Measure-Object -Maximum).Maximum
            MaxTickMs = ($rows.tickMaxMs | Measure-Object -Maximum).Maximum
        }
    } | Format-Table -AutoSize
} else {
    Write-Output "`nNo player ping samples matched this window. Samples begin after the 2026-09-08 network update."
}

if ($closed.Count) {
    Write-Output "`nCompleted WebSocket relay sessions"
    $closed | Group-Object gameName | ForEach-Object {
        $rows = @($_.Group)
        $bytes = ($rows | Measure-Object upstreamToClientBytes -Sum).Sum
        $duration = ($rows | Measure-Object durationMs -Sum).Sum
        [pscustomobject]@{
            Player = $_.Name
            Connections = $rows.Count
            Minutes = [Math]::Round($duration / 60000, 1)
            DownstreamMB = [Math]::Round($bytes / 1MB, 1)
            MeanDownKbps = if ($duration -gt 0) { [Math]::Round(($bytes * 8) / $duration, 0) } else { 0 }
            MaxClientQueueKB = [Math]::Round((($rows.maxClientQueueBytes | Measure-Object -Maximum).Maximum) / 1KB, 1)
            Edge = (($rows | Where-Object edgeColo | Select-Object -ExpandProperty edgeColo -Unique) -join ",")
        }
    } | Sort-Object DownstreamMB -Descending | Format-Table -AutoSize
} else {
    Write-Output "`nNo completed game relay sessions matched this window."
}

if ($alerts.Count) {
    Write-Output "`nLatency alerts"
    $alerts | Select-Object at,event,player,pingMs,tickP95Ms,tickMaxMs | Format-Table -AutoSize
}
