param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$serverRoot = Join-Path $projectRoot 'server'
$pluginRoot = Join-Path $serverRoot 'custom-plugins\JasprGear'
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $pluginRoot 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$resources = Join-Path $pluginRoot 'resources'
$candidateRoot = Join-Path $projectRoot 'candidate\gear'
$classes = Join-Path $candidateRoot ('classes-' + [Guid]::NewGuid().ToString('N'))
$outputJar = Join-Path $candidateRoot 'JasprGear.jar'
$jdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
$java = Join-Path $jdkRoot 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw "Java compiler not found: $javac" }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$paper = Join-Path $serverRoot 'cache\patched_1.12.2.jar'
$ErrorActionPreference = 'Continue'
& $javac --release 8 -encoding UTF-8 -Xlint:-options -cp $paper -d $classes $sourceFiles 2>&1 | ForEach-Object { "$_" }
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { throw "Plugin compilation failed with exit code $LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $resources 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml')
Copy-Item -LiteralPath (Join-Path $resources 'config.yml') -Destination (Join-Path $classes 'config.yml')
if (Test-Path -LiteralPath $outputJar) { Remove-Item -LiteralPath $outputJar -Force }
& $jar --create --file $outputJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "Plugin packaging failed with exit code $LASTEXITCODE" }
Remove-Item -LiteralPath $classes -Recurse -Force
# Canonical item/icon SNBT for the browser catalogue and panel (no server needed).
$ErrorActionPreference = 'Continue'
& $java -cp "$paper;$outputJar" chat.jaspr.gear.GearExport (Join-Path $candidateRoot 'gear-catalog.json') 2>&1 | Out-Null
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { throw "GearExport failed with exit code $LASTEXITCODE" }
Write-Output "Built candidate only (not installed): $outputJar"
Get-FileHash -Algorithm SHA256 -LiteralPath $outputJar
