@echo off
echo Creating full project backup (source code + resources)...

:: Change directory to the script's location to ensure paths are correct
cd /D "%~dp0"
echo Running from: %cd%

:: Create a backups directory if it doesn't exist
if not exist "backups" (
    mkdir "backups"
    echo Created 'backups' directory.
)

:: --- Get timestamp using PowerShell (more reliable than wmic) ---
echo.
echo Getting timestamp...
where powershell >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: PowerShell is not available on this system. Cannot generate a timestamp.
    pause
    exit /b
)
for /f "delims=" %%a in ('powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'"') do set "timestamp=%%a"
echo Timestamp: %timestamp%


:: Define the destination archive name
set "destination_zip=backups\ListTelegram-Project-%timestamp%.zip"

:: Check if tar command exists
where tar >nul 2>nul
if %errorlevel% neq 0 (
    echo.
    echo ERROR: 'tar' command not found. This script requires a modern version of Windows 10/11.
    echo Please consider updating Windows or finding an alternative archiving tool.
    pause
    exit /b
)

:: Use tar to create a zip archive of the current directory, excluding unnecessary folders
echo.
echo Archiving project files... Please wait.
tar -a -c -f "%destination_zip%" --exclude="backups" --exclude="target" --exclude=".idea" .

:: Check if the archive was created
if exist "%destination_zip%" (
    echo.
    echo Backup created successfully!
    echo %destination_zip%
) else (
    echo.
    echo ERROR: Failed to create backup archive.
    echo Please check for errors above.
)

echo.
pause
