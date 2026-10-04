@echo off
setlocal EnableExtensions
title Navine-Ghost-Client
cd /d "%~dp0"

set "INJECTOR=%~dp0Navine-Client-Ghost.jar"
if not exist "%INJECTOR%" (
  echo.
  echo [ERROR] Put Navine-Client-Ghost.jar in the same folder as this file.
  echo Folder: %~dp0
  echo.
  pause
  exit /b 1
)

set "JAVA_EXE="
if exist "%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\java.exe"
if not defined JAVA_EXE if exist "%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\BellSoft\LibericaJDK-25\bin\java.exe" set "JAVA_EXE=C:\Program Files\BellSoft\LibericaJDK-25\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\BellSoft\LibericaJDK-17\bin\java.exe" set "JAVA_EXE=C:\Program Files\BellSoft\LibericaJDK-17\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"

if not defined JAVA_EXE (
  echo.
  echo [ERROR] Java not found.
  echo.
  pause
  exit /b 1
)

echo Navine-Ghost-Client
echo Using Java: %JAVA_EXE%
echo.

"%JAVA_EXE%" -jar "%INJECTOR%" start
if not "%ERRORLEVEL%"=="0" (
  echo.
  echo [ERROR] Failed with code %ERRORLEVEL%.
  pause
  exit /b %ERRORLEVEL%
)

echo.
echo Done. Press ` in-game to open the ghost client GUI.
pause
exit /b 0
