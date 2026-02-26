@echo off
setlocal
set BASEDIR=%~dp0
set WRAPPER_DIR=%BASEDIR%\.mvn\wrapper
set JAR=%WRAPPER_DIR%\maven-wrapper.jar
set PROPS=%WRAPPER_DIR%\maven-wrapper.properties

if not exist "%JAR%" (
  echo Downloading Maven Wrapper jar...
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  for /f "tokens=2 delims==" %%A in ('findstr /b "wrapperUrl=" "%PROPS%"') do set WRAPPER_URL=%%A
  if "%WRAPPER_URL%"=="" set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
  powershell -Command "Invoke-WebRequest -UseBasicParsing %WRAPPER_URL% -OutFile %JAR%"
)

set JAVA_EXEC=java
if not "%JAVA_HOME%"=="" set JAVA_EXEC=%JAVA_HOME%\bin\java

"%JAVA_EXEC%" -jar "%JAR%" %*
