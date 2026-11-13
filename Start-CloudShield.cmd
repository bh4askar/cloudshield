@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0"
if exist ".env.local" (
  for /f "usebackq tokens=1,* delims==" %%A in (".env.local") do (
    if "%%A"=="GOOGLE_CLIENT_ID" set "GOOGLE_CLIENT_ID=%%B"
    if "%%A"=="GOOGLE_CLIENT_SECRET" set "GOOGLE_CLIENT_SECRET=%%B"
  )
)
set "CLOUDSHIELD_MODE=local"
set "CLOUDSHIELD_DATA_DIR=%~dp0data"
if exist "%~dp0runtime\windows\bin\java.exe" (
  set "CS_JAVA=%~dp0runtime\windows\bin\java.exe"
) else if defined JAVA_HOME (
  set "CS_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "CS_JAVA=java"
)
echo.
echo   CloudShield - local demo
echo   Open http://127.0.0.1:8080 after the server starts.
echo   Username: admin
echo   Password: CloudShield-Demo-2026!
echo   Keep this window open. Press Ctrl+C to stop.
echo.
if not exist "%~dp0web\target\cloudshield-web-1.0.0.jar" (
  echo Build the project first: mvn clean package
  pause
  exit /b 1
)
"%CS_JAVA%" -Xms128m -Xmx512m -jar "%~dp0web\target\cloudshield-web-1.0.0.jar"
if errorlevel 1 pause
