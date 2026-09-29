@echo off
rem Launch ConcLandopolis. Uses %JAVA_HOME%\bin\java.exe if JAVA_HOME is set, otherwise java on PATH.
setlocal
cd /d "%~dp0"
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -jar "%~dp0ConcLandopolis.jar" %*
endlocal