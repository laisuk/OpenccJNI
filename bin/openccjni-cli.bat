@echo off
setlocal enabledelayedexpansion

set "JRE=java"
set "LIB=lib\*"

rem Find the newest JAR inside openccjni-cli\build\libs
for /f "delims=" %%F in ('dir /b /o-d "openccjni-cli\build\libs\openccjni-cli-*.jar" 2^>nul') do (
    set "JAR=openccjni-cli\build\libs\%%F"
    goto :found
)

echo Error: No JAR found in openccjni-cli\build\libs
exit /b 1

:found
"%JRE%" --enable-native-access=ALL-UNNAMED -cp "%JAR%;%LIB%" openccjnicli.Main %*
