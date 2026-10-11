param([string]$Api='C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\cache\patched_1.12.2.jar')
$ErrorActionPreference='Stop'
$combatRoot=Split-Path -Parent $PSScriptRoot
$combatBuild=Join-Path $combatRoot 'build'
New-Item -ItemType Directory -Force -Path "$combatBuild/classes" | Out-Null
$combatSources=@(Get-ChildItem -LiteralPath "$combatRoot/src" -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac --release 8 -encoding UTF-8 -cp $Api -d "$combatBuild/classes" @combatSources
if($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Copy-Item "$combatRoot/resources/*" "$combatBuild/classes" -Recurse -Force
Copy-Item -LiteralPath "$combatRoot/THIRD_PARTY_NOTICES.txt" -Destination "$combatBuild/classes/THIRD_PARTY_NOTICES.txt" -Force
& jar cf "$combatBuild/JasprBetterCombat.jar" -C "$combatBuild/classes" .
if($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
Get-FileHash "$combatBuild/JasprBetterCombat.jar"
