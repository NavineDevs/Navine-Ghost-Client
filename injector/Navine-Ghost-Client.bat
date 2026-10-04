@echo off
setlocal EnableExtensions
title NavineGhostInjector
cd /d "%~dp0"
set "APP=%~dp0NavineGhostInjector.exe"
if not exist "%APP%" set "APP=%~dp0native\out\NavineGhostInjector.exe"
if not exist "%APP%" (
  echo [ERROR] NavineGhostInjector.exe not found. Build the project first.
  pause
  exit /b 1
)
start "" "%APP%"
exit /b 0
