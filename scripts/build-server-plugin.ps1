param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$serverRoot = Join-Path $projectRoot 'server'
$pluginRoot = Join-Path $serverRoot 'custom-plugins\TestServerControl'
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $pluginRoot 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$resources = Join-Path $pluginRoot 'resources'
$candidateRoot = Join-Path $projectRoot 'candidate\jaspr-sso'
$classes = Join-Path $candidateRoot ('classes-' + [Guid]::NewGuid().ToString('N'))
$outputJar = Join-Path $candidateRoot 'TestServerControl.jar'
$jdkRoot = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $javac)) { throw "Java compiler not found: $javac" }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$compileClasspath = @('cache\patched_1.12.2.jar','plugins\EaglerXServer.jar','plugins\AuthMe.jar') | ForEach-Object { Join-Path $serverRoot $_ }
& $javac --release 8 -encoding UTF-8 -cp ($compileClasspath -join ';') -d $classes $sourceFiles
if ($LASTEXITCODE -ne 0) { throw "Plugin compilation failed with exit code $LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $resources 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml')
Copy-Item -LiteralPath (Join-Path $resources 'config.yml') -Destination (Join-Path $classes 'config.yml')
& $jar --create --file $outputJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "Plugin packaging failed with exit code $LASTEXITCODE" }
Write-Output "Built candidate only (not installed): $outputJar"
Get-FileHash -Algorithm SHA256 -LiteralPath $outputJar
