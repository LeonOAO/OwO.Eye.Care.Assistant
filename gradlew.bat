@echo off
setlocal
set "GRADLE_VERSION=8.7"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "BOOTSTRAP_DIR=%GRADLE_USER_HOME%\bootstrap"
set "GRADLE_HOME=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%"
set "ARCHIVE=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BOOTSTRAP_DIR%" mkdir "%BOOTSTRAP_DIR%"
  if not exist "%ARCHIVE%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    curl.exe -fL --retry 3 --retry-delay 2 "https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip" -o "%ARCHIVE%"
    if errorlevel 1 exit /b 1
  )
  powershell.exe -NoProfile -Command "Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%BOOTSTRAP_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
