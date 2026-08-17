@echo off
setlocal
set "REPOSITORY_ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%REPOSITORY_ROOT%scripts\start-preview.ps1" -QuickPlay -BuildIfMissing -OpenBrowser
if errorlevel 1 (
    echo.
    echo TroubleshootJS could not start. See the message above for the required JDK 8, build, or preview detail.
    pause
    exit /b 1
)
endlocal
exit /b 0
