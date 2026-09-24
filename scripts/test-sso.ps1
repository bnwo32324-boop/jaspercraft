param()
$ErrorActionPreference = 'Stop'
$ssoProject = Split-Path -Parent $PSScriptRoot
Push-Location $ssoProject
try {
    & (Join-Path $PSScriptRoot 'build-server-plugin.ps1')
    & (Join-Path $PSScriptRoot 'build-site.ps1')
    $ssoClasses = Join-Path $ssoProject ('candidate\sso-tests-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $ssoClasses | Out-Null
    $ssoClasspath = 'candidate\jaspr-sso\TestServerControl.jar;server\cache\patched_1.12.2.jar;server\plugins\EaglerXServer.jar;server\plugins\AuthMe.jar'
    & javac -encoding UTF-8 -cp $ssoClasspath -d $ssoClasses tests\java\local\eagler\testserver\SsoContractTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Java SSO test compilation failed.' }
    & java -ea -cp ($ssoClasses + ';' + $ssoClasspath) local.eagler.testserver.SsoContractTest
    if ($LASTEXITCODE -ne 0) { throw 'Java SSO tests failed.' }
    & node --test tests\sso-browser.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Browser SSO tests failed.' }
} finally { Pop-Location }
