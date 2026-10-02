@echo off
REM Projektlokaler graphify-Aufruf fuer PowerShell/cmd, auch aus Git-Worktrees.
REM Pendant zu scripts/graphify: sucht .graphify-venv im eigenen Checkout und
REM sonst im Haupt-Checkout (ein Worktree hat kein eigenes venv).
REM Braucht git >= 2.31 (--path-format).
setlocal
set "REPO=%~dp0.."
set "BIN=%REPO%\.graphify-venv\Scripts\graphify.exe"
if exist "%BIN%" goto start
for /f "delims=" %%i in ('git -C "%REPO%" rev-parse --path-format^=absolute --git-common-dir 2^>nul') do set "COMMON=%%i"
if defined COMMON for %%j in ("%COMMON%\..") do set "BIN=%%~fj\.graphify-venv\Scripts\graphify.exe"
if exist "%BIN%" goto start
echo graphify ist nicht installiert (kein .graphify-venv im Checkout oder Haupt-Checkout). 1>&2
echo Einrichten im Haupt-Checkout mit: 1>&2
echo   uv venv --python 3.12 .graphify-venv 1>&2
echo   uv pip install --python .graphify-venv\Scripts\python.exe "graphifyy[sql]" 1>&2
exit /b 127
:start
"%BIN%" %*
set "STATUS=%ERRORLEVEL%"
if /i not "%~1"=="update" exit /b %STATUS%
if not defined COMMON exit /b %STATUS%
echo. 1>&2
echo Hinweis: Das ist ein Worktree. graphify-out\ hier nicht committen (32-MB-Datei, 1>&2
echo Konflikte mit parallelen Sitzungen). Vor einem Rebase: git checkout -- graphify-out 1>&2
exit /b %STATUS%
