@echo off
REM Publishes VolmLib to the local Maven repository under the coordinate this project
REM resolves by default: com.github.VolmitSoftware:VolmLib:master-SNAPSHOT.
REM
REM VolmLib is normally picked up as a composite build straight from source, so this is
REM only needed when you build with -PuseLocalVolmLib=false, or when another tool has to
REM resolve VolmLib from the local Maven repository rather than the included build.
REM
REM The lookup mirrors settings.gradle: it walks up from this script looking for a
REM VolmLib checkout sitting beside the project root. Set VOLMLIB_DIR to override.
REM Any extra arguments are forwarded to gradle.

setlocal

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "VOLMLIB_DIRECTORY="

if defined VOLMLIB_DIR call :check "%VOLMLIB_DIR%"
if not defined VOLMLIB_DIRECTORY call :search "%SCRIPT_DIR%"

if not defined VOLMLIB_DIRECTORY (
    echo Could not find a VolmLib checkout. 1>&2
    echo Expected a VolmLib directory beside this project root, or VOLMLIB_DIR pointing at one. 1>&2
    exit /b 1
)

echo ==^> Publishing VolmLib from %VOLMLIB_DIRECTORY%

pushd "%VOLMLIB_DIRECTORY%"
if errorlevel 1 exit /b 1

call gradlew.bat publishToMavenLocal -Pgroup=com.github.VolmitSoftware -Pversion=master-SNAPSHOT -PvolmLibArtifactId=VolmLib %*
set "PUBLISH_RESULT=%ERRORLEVEL%"

popd
exit /b %PUBLISH_RESULT%

:check
if exist "%~1\settings.gradle" set "VOLMLIB_DIRECTORY=%~f1"
if exist "%~1\settings.gradle.kts" set "VOLMLIB_DIRECTORY=%~f1"
goto :eof

:search
set "CURRENT=%~f1"
:search_loop
if exist "%CURRENT%\VolmLib\settings.gradle" (
    set "VOLMLIB_DIRECTORY=%CURRENT%\VolmLib"
    goto :eof
)
if exist "%CURRENT%\VolmLib\settings.gradle.kts" (
    set "VOLMLIB_DIRECTORY=%CURRENT%\VolmLib"
    goto :eof
)
for %%I in ("%CURRENT%\..") do set "PARENT=%%~fI"
if /i "%PARENT%"=="%CURRENT%" goto :eof
set "CURRENT=%PARENT%"
goto :search_loop
