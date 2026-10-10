@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if exist "%WRAPPER%" (
  java -classpath "%WRAPPER%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
set "GV=9.5.0"
if defined GRADLE_USER_HOME (set "BASE=%GRADLE_USER_HOME%\fsr-bootstrap\%GV%") else (set "BASE=%USERPROFILE%\.gradle\fsr-bootstrap\%GV%")
set "ZIP=%BASE%\gradle-%GV%-bin.zip"
set "DIST=%BASE%\gradle-%GV%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GV%-bin.zip' -OutFile '%ZIP%'; Expand-Archive -Force '%ZIP%' '%BASE%'"
)
call "%DIST%\bin\gradle.bat" %*
