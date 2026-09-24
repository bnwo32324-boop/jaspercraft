param()
$ErrorActionPreference='Stop'
$apocRoot=Split-Path -Parent $PSScriptRoot
$sourceRoot=Join-Path $apocRoot 'server\custom-plugins\JasprApocalypse'
$candidateRoot=Join-Path $apocRoot 'candidate\apocalypse'
$classes=Join-Path $candidateRoot ('classes-'+[Guid]::NewGuid().ToString('N'))
$jdk='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'src') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
$classpath=(Join-Path $apocRoot 'server\cache\patched_1.12.2.jar')+';'+(Join-Path $apocRoot 'server\plugins\AuthMe.jar')
& (Join-Path $jdk 'javac.exe') --release 8 -encoding UTF-8 -cp $classpath -d $classes $sources
if($LASTEXITCODE -ne 0){throw 'Apocalypse compilation failed.'}
Copy-Item -LiteralPath (Join-Path $sourceRoot 'resources\plugin.yml') -Destination $classes
Copy-Item -LiteralPath (Join-Path $sourceRoot 'resources\config.yml') -Destination $classes
$output=Join-Path $candidateRoot 'JasprApocalypse.jar'
& (Join-Path $jdk 'jar.exe') --create --file $output -C $classes .
if($LASTEXITCODE -ne 0){throw 'Apocalypse packaging failed.'}
Get-FileHash -Algorithm SHA256 -LiteralPath $output
