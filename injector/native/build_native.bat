@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
for /f "usebackq delims=" %%I in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%I"
if not defined VSINSTALL (
  echo [ERROR] Visual Studio C++ tools not found.
  exit /b 1
)

call "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 exit /b 1

set "JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-25"
if not exist "%JAVA_HOME%\include\jni.h" (
  echo [ERROR] jni.h not found under %JAVA_HOME%
  exit /b 1
)

if not exist out mkdir out

set "AGENT_JAR=%~1"
set "PAYLOAD_JAR=%~2"
set "PRODUCT_NAME=%~3"
if "%PRODUCT_NAME%"=="" set "PRODUCT_NAME=NavineGhostClientInjector"
set "EXTRA_DEFS=%~4"
set "PAYLOAD_VANILLA_JAR=%~5"
if "%PAYLOAD_VANILLA_JAR%"=="" set "PAYLOAD_VANILLA_JAR=%PAYLOAD_JAR%"
if not exist "%PAYLOAD_VANILLA_JAR%" set "PAYLOAD_VANILLA_JAR=%PAYLOAD_JAR%"

cl /nologo /O2 /MT /LD /EHsc /std:c++17 ^
  /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
  ghost_java_inject.cpp ^
  /Fe:out\ghost_java_inject.dll /link /DLL
if errorlevel 1 exit /b 1

cl /nologo /O2 /MT /EHsc /std:c++17 ghost_dll_inject.cpp /Fe:out\ghost-dll-inject.exe /link advapi32.lib
if errorlevel 1 exit /b 1

cl /nologo /O2 /MT /EHsc /std:c++17 /DUNICODE /D_UNICODE %EXTRA_DEFS% inject_core.cpp ghost_inject_cli.cpp /Fe:out\ghost-inject-cli.exe /link /SUBSYSTEM:CONSOLE shell32.lib advapi32.lib user32.lib
if errorlevel 1 exit /b 1

set "RC_FILE=%~dp0out\ghost_gui.rc"
set "RES_FILE=%~dp0out\ghost_gui.res"
set "OUT_EXE=out\%PRODUCT_NAME%.exe"
set "CXXFLAGS=/nologo /O2 /MT /EHsc /std:c++17 /utf-8 /DUNICODE /D_UNICODE %EXTRA_DEFS%"

if "%AGENT_JAR%"=="" goto :gui_no_res
if "%PAYLOAD_JAR%"=="" goto :gui_no_res
if not exist "%AGENT_JAR%" goto :gui_no_res
if not exist "%PAYLOAD_JAR%" goto :gui_no_res

set "AGENT_ESC=%AGENT_JAR:\=/%"
set "PAYLOAD_ESC=%PAYLOAD_JAR:\=/%"
set "VANILLA_ESC=%PAYLOAD_VANILLA_JAR:\=/%"
set "DLL_ESC=%~dp0out\ghost_java_inject.dll"
set "DLL_ESC=%DLL_ESC:\=/%"

> "%RC_FILE%" (
echo 101 RCDATA "%AGENT_ESC%"
echo 102 RCDATA "%PAYLOAD_ESC%"
echo 103 RCDATA "%DLL_ESC%"
echo 104 RCDATA "%VANILLA_ESC%"
)
rc /nologo /fo "%RES_FILE%" "%RC_FILE%"
if errorlevel 1 exit /b 1

cl %CXXFLAGS% inject_core.cpp ghost_gui.cpp "%RES_FILE%" /Fe:%OUT_EXE% /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib shell32.lib advapi32.lib msimg32.lib
if errorlevel 1 exit /b 1
goto :done

:gui_no_res
cl %CXXFLAGS% inject_core.cpp ghost_gui.cpp /Fe:%OUT_EXE% /link /SUBSYSTEM:WINDOWS user32.lib gdi32.lib shell32.lib advapi32.lib msimg32.lib
if errorlevel 1 exit /b 1

:done
echo.
echo Built:
echo   %~dp0out\ghost_java_inject.dll
echo   %~dp0out\ghost-dll-inject.exe
echo   %~dp0out\ghost-inject-cli.exe
echo   %~dp0%OUT_EXE%
exit /b 0
