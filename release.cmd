@echo off
setlocal

if "%~1"=="" (
    echo Usage: release ^<version^>
    echo Example: release 0.1.0
    exit /b 1
)

set "VERSION=%~1"

echo Releasing v%VERSION%...

:: Update mod.version in gradle.properties
powershell -Command "(Get-Content gradle.properties) -replace 'mod\.version=.*', 'mod.version=%VERSION%' | Set-Content gradle.properties"

:: Commit and tag
git add gradle.properties
git commit -m "release: v%VERSION%"
git tag "v%VERSION%"

echo.
echo Tagged v%VERSION%. To publish:
echo   git push origin master v%VERSION%
