param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'maintenance-mode'), "manual maintenance`r`n")
[IO.File]::WriteAllText((Join-Path $runtimeRoot 'game-server-stop.request'), "stop`r`n")
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $status = Invoke-RestMethod -Uri 'http://127.0.0.1:3310/status' -TimeoutSec 3
        if ($status.server -in @('maintenance','stopped')) { Write-Output 'Paper is offline in maintenance mode. Run start-game-server.ps1 to restore always-on service.'; exit 0 }
    } catch {}
}
throw 'The Paper server did not stop gracefully within two minutes.'