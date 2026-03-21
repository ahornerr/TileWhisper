@echo off
rem Java wrapper that enables assertions and forwards all args to actual java binary

rem Bolt passes java binary path as first arg, then remaining args
set "JAVA_BIN=%~1"
shift

rem Rebuild remaining arguments after the first
set "ARGS="
:loop
if "%~1"=="" goto run
if defined ARGS (
    set "ARGS=%ARGS% %1"
) else (
    set "ARGS=%1"
)
shift
goto loop

:run
"%JAVA_BIN%" -ea %ARGS%
