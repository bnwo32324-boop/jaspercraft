param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot '.runtime'
Remove-Item -LiteralPath (Join-Path $runtimeRoot 'maintenance-mode') -Force -ErrorAction SilentlyContinue
& (Join-Path $PSScriptRoot 'start-game-gateway.ps1')
try { Invoke-RestMethod -Uri 'http://127.0.0.1:3310/health' -TimeoutSec 5 | Out-Null } catch {}
for ($attempt = 0; $attempt -lt 180; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $status = Invoke-RestMethod -Uri 'http://127.0.0.1:3310/status' -TimeoutSec 3
        if ($status.server -eq 'running') { Write-Output 'Eaglercraft 1.12.2 Paper server is ready and configured to remain online.'; exit 0 }
    } catch {}
}
throw 'The Paper server did not become ready within three minutes.'