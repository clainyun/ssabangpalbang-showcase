@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare-onestore-apk.ps1" %*
exit /b %ERRORLEVEL%
