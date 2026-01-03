@echo off
echo ========================================
echo Entangle App - Build Verification
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Cleaning previous builds...
call gradlew.bat clean
if errorlevel 1 (
    echo ERROR: Clean failed!
    pause
    exit /b 1
)

echo.
echo Step 2: Assembling debug APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERROR: Build failed! Please check the errors above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo The app has been built successfully.
echo APK location: app\build\outputs\apk\debug\app-debug.apk
echo.
echo Next steps:
echo 1. Open Android Studio
echo 2. Click 'File' -^> 'Sync Project with Gradle Files'
echo 3. Wait for sync to complete
echo 4. The Run button should now be enabled
echo.
pause
