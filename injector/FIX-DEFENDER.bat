@echo off
:: Run as Administrator once. Stops Windows from deleting the EXE.
net session >nul 2>&1
if errorlevel 1 (
  echo Requesting Administrator...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b 0
)

set "ROOT=%~dp0"
set "PROJ=C:\Users\hitbo\Downloads\Navine-Client-Ghost"
set "OUT=C:\Users\hitbo\Downloads"
set "NATIVE=%ROOT%native\out"

powershell -NoProfile -Command ^
  "Add-MpPreference -ExclusionPath '%PROJ%'; Add-MpPreference -ExclusionPath '%ROOT%'; Add-MpPreference -ExclusionPath '%NATIVE%'; Add-MpPreference -ExclusionPath '%OUT%'; Add-MpPreference -ExclusionProcess 'NavineGhostClient.exe'; Add-MpPreference -ExclusionProcess 'NavineGhostClientInjector.exe'"

echo Defender exclusions added.
echo Rebuild from project with Gradle :injector:deployInjector
pause
