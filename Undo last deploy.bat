@echo off
rem JasperCraft: undo the last deploy. Runs only when you double-click it.
title JasperCraft - undo last deploy
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\deploy\Undo-LastDeploy.ps1"
if %ERRORLEVEL% GEQ 100 exit /b 0
echo.
echo Undo could not start (error %ERRORLEVEL%). Nothing was changed. Please tell Claude.
pause
