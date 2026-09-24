param()
$ErrorActionPreference='Stop'
$biomeRoot=Split-Path -Parent $PSScriptRoot
$biomeSource=Join-Path $biomeRoot 'server\custom-plugins\JasprHorrorBiomes'
$biomeCandidate=Join-Path $biomeRoot 'candidate\horror-biomes'
$biomeClasses=Join-Path $biomeCandidate ('classes-'+[Guid]::NewGuid().ToString('N'))
$biomeJdk='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin'
New-Item -ItemType Directory -Force -Path $biomeClasses | Out-Null
$biomeSources=@(Get-ChildItem -LiteralPath (Join-Path $biomeSource 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $biomeJdk 'javac.exe') --release 8 -encoding UTF-8 -cp (Join-Path $biomeRoot 'server\cache\patched_1.12.2.jar') -d $biomeClasses $biomeSources
if($LASTEXITCODE -ne 0){throw 'Horror biome compilation failed.'}
Get-ChildItem -LiteralPath (Join-Path $biomeSource 'resources') | Copy-Item -Destination $biomeClasses -Recurse
$biomeJar=Join-Path $biomeCandidate 'JasprHorrorBiomes.jar'
& (Join-Path $biomeJdk 'jar.exe') --create --file $biomeJar -C $biomeClasses .
if($LASTEXITCODE -ne 0){throw 'Horror biome packaging failed.'}
Get-FileHash -Algorithm SHA256 -LiteralPath $biomeJar
