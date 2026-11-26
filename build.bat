@echo off
setlocal

set "ROOT=%~dp0"
set "ANDROID_DIR=%ROOT%android"
set "MISSING_PREREQUISITE="
if not exist "%ROOT%build\dxvk-source\PINNED_REVISION" set "MISSING_PREREQUISITE=1"
if not exist "%ROOT%src\recomp\gen\recomp_*.c" set "MISSING_PREREQUISITE=1"
if not exist "%ROOT%..\pcrecomp\runtime\recomp32\recomp_types.h" set "MISSING_PREREQUISITE=1"
if defined MISSING_PREREQUISITE (
    echo ERROR: Android build prerequisites are missing:
    if not exist "%ROOT%build\dxvk-source\PINNED_REVISION" (
        echo   Prepare pinned DXVK. See ports\dxvk_android\README.md.
        echo   git clone --branch native-1.9.2b https://github.com/Joshua-Ashton/dxvk-native.git ..\reference\dxvk-native
        echo   python ports\dxvk_android\prepare.py ..\reference\dxvk-native build\dxvk-source
    )
    if not exist "%ROOT%src\recomp\gen\recomp_*.c" echo   Generate C sources from your own SPEED2.EXE as described in README.md.
    if not exist "%ROOT%..\pcrecomp\runtime\recomp32\recomp_types.h" echo   Set up the patched pcrecomp checkout at ..\pcrecomp as described in README.md.
    exit /b 1
)

set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK set "SDK=%ANDROID_HOME%"

if not defined SDK (
    for /f "usebackq delims=" %%S in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='%ANDROID_DIR%local.properties'; if (Test-Path -LiteralPath $p) { $line=Get-Content -LiteralPath $p | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1; if ($line) { $line.Substring(8).Replace('\\:',':').Replace('\\\\','\') } }"`) do set "SDK=%%S"
)
if not defined SDK set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
if not exist "%SDK%\ndk" (
    echo ERROR: Android NDK directory not found under "%SDK%".
    echo Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK.
    exit /b 1
)
if not exist "%SDK%\cmake" (
    echo ERROR: Android CMake directory not found under "%SDK%".
    exit /b 1
)

set "NDK_VERSION="
set "CMAKE_VERSION="
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -LiteralPath '%SDK%\ndk' -Directory | Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending | Select-Object -First 1 -ExpandProperty Name"`) do set "NDK_VERSION=%%V"
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -LiteralPath '%SDK%\cmake' -Directory | Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending | Select-Object -First 1 -ExpandProperty Name"`) do set "CMAKE_VERSION=%%V"

if not defined NDK_VERSION (
    echo ERROR: No Android NDK version is installed under "%SDK%\ndk".
    exit /b 1
)
if not defined CMAKE_VERSION (
    echo ERROR: No Android CMake version is installed under "%SDK%\cmake".
    exit /b 1
)

if not exist "%ROOT%build\dxvk-source\generated\*.h" (
    set "PATH=%SDK%\emulator\lib64\vulkan;%SDK%\ndk\%NDK_VERSION%\shader-tools\windows-x86_64;%PATH%"
    where glslangValidator.exe >nul 2>nul
    if errorlevel 1 (
        echo ERROR: glslangValidator.exe was not found in the Android SDK or PATH.
        exit /b 1
    )
    where spirv-val.exe >nul 2>nul
    if errorlevel 1 (
        echo ERROR: spirv-val.exe was not found in the selected NDK or PATH.
        exit /b 1
    )
    echo Generating and validating DXVK Vulkan 1.1 shaders...
    pushd "%ROOT%"
    python ports\dxvk_android\shaders.py build\dxvk-source
    set "SHADER_RESULT=%ERRORLEVEL%"
    popd
    if not "%SHADER_RESULT%"=="0" exit /b %SHADER_RESULT%
)

echo Android SDK: "%SDK%"
echo Using NDK %NDK_VERSION% and CMake %CMAKE_VERSION%
if not defined NFS_ABI set "NFS_ABI=armeabi-v7a"
echo Building APK for %NFS_ABI%...

pushd "%ANDROID_DIR%"
call gradlew.bat --no-daemon -PnfsNdkVersion=%NDK_VERSION% -PnfsCmakeVersion=%CMAKE_VERSION% -PnfsAbi=%NFS_ABI% assembleDebug
set "BUILD_RESULT=%ERRORLEVEL%"
popd

if not "%BUILD_RESULT%"=="0" exit /b %BUILD_RESULT%
echo APK: "%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
exit /b 0
