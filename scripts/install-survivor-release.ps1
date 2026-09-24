param([Parameter(Mandatory=$true)][string]$Release)
$ErrorActionPreference='Stop'
$survivorRoot=(Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$survivorStage=(Resolve-Path -LiteralPath (Join-Path $survivorRoot $Release)).Path
if(-not $survivorStage.StartsWith((Join-Path $survivorRoot 'candidate\survivor-release-'),[StringComparison]::OrdinalIgnoreCase)){throw 'Not an exact survivor release candidate.'}
$survivorPlan=Get-Content -LiteralPath (Join-Path $survivorStage 'plan.json') -Raw | ConvertFrom-Json
if($survivorPlan.worldReset -ne $false){throw 'This release must not reset terrain.'}
$survivorStatus=Invoke-RestMethod 'http://127.0.0.1:3310/status'
if($survivorStatus.server -ne 'maintenance' -or -not(Test-Path -LiteralPath (Join-Path $survivorRoot '.runtime\maintenance-mode'))){throw 'Gracefully stop the game in maintenance first.'}
$survivorPidFile=Join-Path $survivorRoot '.runtime\paper-server.pid'
if(Test-Path -LiteralPath $survivorPidFile){$survivorPid=[int](Get-Content -LiteralPath $survivorPidFile -Raw);if(Get-Process -Id $survivorPid -ErrorAction SilentlyContinue){throw 'Paper is still running.'}}
if(Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue){throw 'Game port is still listening.'}
$survivorAllowed=@('site/classes.js','site/assets.epk','site/index.html','site/client.html','site/jaspr-sso.js','site/jaspr-client.js','server/plugins/JasprApocalypse.jar','server/plugins/JasprHorrorBiomes.jar')
if($survivorPlan.artifacts.Count -ne $survivorAllowed.Count){throw 'Incomplete release artifact list.'}
foreach($survivorArtifact in $survivorPlan.artifacts){
 if($survivorArtifact.target -notin $survivorAllowed){throw 'Unexpected release target.'}
 $survivorCurrent=Join-Path $survivorRoot $survivorArtifact.target
 $survivorNext=Join-Path $survivorStage $survivorArtifact.target
 if((Get-FileHash -LiteralPath $survivorCurrent -Algorithm SHA256).Hash -ne $survivorArtifact.before){throw ('Live artifact changed: '+$survivorArtifact.target)}
 if((Get-FileHash -LiteralPath $survivorNext -Algorithm SHA256).Hash -ne $survivorArtifact.sha256){throw ('Candidate changed: '+$survivorArtifact.target)}
}
# Hash every terrain/player file and plugin data file with Paper stopped, including
# AuthMe and the finite-cache journal. These paths are never copied over or deleted.
$survivorProtected=[ordered]@{}
$survivorDataRoots=@('server/world','server/world_nether','server/world_the_end','server/jaspr_backrooms')
$survivorDataRoots+=@(Get-ChildItem -LiteralPath (Join-Path $survivorRoot 'server/plugins') -Directory | ForEach-Object { 'server/plugins/'+$_.Name })
foreach($survivorDataRoot in $survivorDataRoots){
 $survivorResolved=(Resolve-Path -LiteralPath (Join-Path $survivorRoot $survivorDataRoot)).Path
 if(-not $survivorResolved.StartsWith((Join-Path $survivorRoot 'server\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Protected data escaped the server directory.'}
 foreach($survivorDataFile in Get-ChildItem -LiteralPath $survivorResolved -Recurse -File){
  if($survivorDataFile.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked protected file rejected.'}
  $survivorRelative=$survivorDataFile.FullName.Substring($survivorRoot.Length+1).Replace('\','/')
  $survivorProtected[$survivorRelative]=(Get-FileHash -LiteralPath $survivorDataFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
 }
}
$survivorRecovery=Join-Path $survivorStage 'previous-artifacts'
New-Item -ItemType Directory -Path $survivorRecovery | Out-Null
foreach($survivorArtifact in $survivorPlan.artifacts){
 $survivorSaved=Join-Path $survivorRecovery $survivorArtifact.target
 New-Item -ItemType Directory -Path (Split-Path -Parent $survivorSaved) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $survivorRoot $survivorArtifact.target) -Destination $survivorSaved
}
try{
 foreach($survivorArtifact in $survivorPlan.artifacts){Copy-Item -LiteralPath (Join-Path $survivorStage $survivorArtifact.target) -Destination (Join-Path $survivorRoot $survivorArtifact.target) -Force}
 foreach($survivorArtifact in $survivorPlan.artifacts){if((Get-FileHash -LiteralPath (Join-Path $survivorRoot $survivorArtifact.target) -Algorithm SHA256).Hash -ne $survivorArtifact.sha256){throw 'Installed artifact mismatch.'}}
 foreach($survivorPair in $survivorProtected.GetEnumerator()){if((Get-FileHash -LiteralPath (Join-Path $survivorRoot $survivorPair.Key) -Algorithm SHA256).Hash -ne $survivorPair.Value){throw ('Protected data changed: '+$survivorPair.Key)}}
 $survivorProof=[ordered]@{verified=$true;verifiedAt=[DateTime]::UtcNow.ToString('o');worldReset=$false;protectedFileCount=$survivorProtected.Count;protectedHashes=$survivorProtected;artifacts=$survivorPlan.artifacts}
 $survivorProof | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $survivorStage 'offline-proof.json') -Encoding UTF8
 Write-Output ('Installed 8 release files; '+$survivorProtected.Count+' terrain, player and plugin-data files verified unchanged. No world reset.')
}catch{
 foreach($survivorArtifact in $survivorPlan.artifacts){Copy-Item -LiteralPath (Join-Path $survivorRecovery $survivorArtifact.target) -Destination (Join-Path $survivorRoot $survivorArtifact.target) -Force}
 throw
}
