@echo off
cd /d "%~dp0"

set JAR_PATH=dist

if not exist "%JAR_PATH%\Geyser.jar" (
    echo Fehler: Geyser.jar nicht gefunden!
    timeout /t 5
    exit /b 1
)

start "" java -jar "%JAR_PATH%\Geyser.jar"
exit


