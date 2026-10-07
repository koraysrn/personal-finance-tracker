@echo off
setlocal enabledelayedexpansion

rem ---------------------------------------------------------------------------
rem Minimal Maven wrapper for Windows.
rem Downloads a local Apache Maven distribution on first use, then delegates
rem every call to it. No global Maven installation is required.
rem ---------------------------------------------------------------------------

set "MVN_VERSION=3.9.9"
set "BASE_DIR=%~dp0"
set "MVN_DIR=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%"
set "MVN_CMD=%MVN_DIR%\bin\mvn.cmd"

if exist "%MVN_CMD%" goto :run

echo Apache Maven %MVN_VERSION% not found, downloading...
set "MVN_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip"
set "MVN_ZIP=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%-bin.zip"

if not exist "%BASE_DIR%.mvn" mkdir "%BASE_DIR%.mvn"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -Uri '%MVN_URL%' -OutFile '%MVN_ZIP%' } catch { Write-Error $_; exit 1 }; Expand-Archive -Path '%MVN_ZIP%' -DestinationPath '%BASE_DIR%.mvn' -Force"

if not exist "%MVN_CMD%" (
    echo Failed to download Apache Maven. Check your internet connection.
    exit /b 1
)

:run
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
