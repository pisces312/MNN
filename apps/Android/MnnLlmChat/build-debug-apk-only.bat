@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================================
echo  MnnLlmChat - Build Standard Debug APK Only
echo  Working dir: %CD%
echo ============================================================
echo.

call gradlew.bat :app:assembleStandardDebug --console=plain
set EXITCODE=%ERRORLEVEL%

echo.
echo ============================================================
if %EXITCODE% == 0 (
    echo  BUILD SUCCESS
    echo  APK: app\build\outputs\apk\standard\debug\app-standard-debug.apk
) else (
    echo  BUILD FAILED  exit code %EXITCODE%
)
echo ============================================================
echo.
pause
exit /b %EXITCODE%
