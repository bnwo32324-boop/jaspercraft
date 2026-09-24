@echo off
rem JasperCraft one-click deployer. Runs only when you double-click it (no scheduled task, no service).
title JasperCraft deployer
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\deploy\Deploy-JasperCraft.ps1"
if %ERRORLEVEL% GEQ 100 exit /b 0
echo.
echo The deployer could not start (error %ERRORLEVEL%). Nothing was changed. Please tell Claude.
pause
