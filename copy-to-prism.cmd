@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM Copy built mods to Prism Launcher instances
REM Usage:
REM   copy-to-prism.cmd          Copy all versions
REM   copy-to-prism.cmd 1.21.4   Copy specified MC version only

set "PRISM_INSTANCES=%APPDATA%\PrismLauncher\instances"
set "PROJECT_DIR=%~dp0"
set "FILTER=%~1"

for /d %%D in ("%PROJECT_DIR%versions\*") do call :process "%%D"
goto :eof

:process
set "VERSION_DIR=%~1"
for %%N in ("%VERSION_DIR%") do set "VERSION_NAME=%%~nxN"
set "INSTANCE_NAME=vrmmod-%VERSION_NAME%"
set "MODS_DIR=%PRISM_INSTANCES%\%INSTANCE_NAME%\minecraft\mods"

if defined FILTER (
    echo %VERSION_NAME% | findstr /b "%FILTER%" >nul 2>&1 || exit /b
)

if not exist "%MODS_DIR%" (
    echo SKIP: %INSTANCE_NAME% [no instance]
    exit /b
)

set "JAR="
for %%J in ("%VERSION_DIR%\build\libs\*.jar") do (
    echo %%~nxJ | findstr /v "\-dev\-shadow" >nul 2>&1 && (
        if not defined JAR set "JAR=%%J"
    )
)

if not defined JAR (
    echo SKIP: %VERSION_NAME% [no build artifact]
    exit /b
)

del /q "%MODS_DIR%\vrmmod-*.jar" 2>nul
for %%J in ("%JAR%") do (
    copy /y "%JAR%" "%MODS_DIR%\%%~nxJ" >nul
    echo OK:   %%~nxJ -- %INSTANCE_NAME%
)
exit /b
