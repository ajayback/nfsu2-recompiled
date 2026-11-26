# Android ARM64 / Vulkan 1.1

Current runtime: [0.4.0 input/audio/device status](ANDROID_INPUT_AUDIO_TEST.md).
The original initial splash has rendered through DXVK on the physical phone.
Prepare the pinned graphics dependency as described in
[DXVK build prerequisites](../ports/dxvk_android/README.md) before running Gradle.

Windows gameplay accepted as validated by the owner on 2026-10-07.
Update: [portable guest integration](ANDROID_RUNTIME.md) now compiles the full
generated game code for ARM64, maps the PE, binds imports and attempts its CRT
entry. The initial bootstrap test results below predate this connection.
The independent `android/` build advances the port without changing the
MSVC x86 reference build. CRT bring-up is connected; the APK is not playable.

## Diagnostic renderer and storage foundation

- Android NativeActivity, Android 10+ (API 29), arm64-v8a by default.
- Vulkan instance requesting 1.1, graphics/present queue and FIFO swapchain.
- Render-pass clear and presentation using only Vulkan 1.1 core and Android
  surface/swapchain extensions. No descriptor indexing, shaderInt64, buffer
  device address, dynamic rendering or synchronization2 requirements.
- Surface format/usage/extent/composite-alpha checks, pause/resume cleanup,
  window recreation and out-of-date swapchain handling.
- Separate render-finished semaphore per swapchain image. Serialized queue
  completion for initial bring-up; frame fences are a later performance step.
- GuestMemory reserves a 4 GiB virtual address space with inaccessible pages;
  commit uses the device page size. Guest addresses remain uint32 offsets;
  host pointers remain 64-bit. Startup checks image/high addresses and rejects
  ranges that wrap. The class is connected to ADDR, the PE loader and explicit
  guest calls; guest heap services are implemented. Reservation is not 4 GiB of
  physical RAM.
- Game files can be stored at `/storage/emulated/0/nfsu2` or
  `/storage/<volume>/nfsu2` (for example, on removable storage). The launcher
  discovers removable volumes from Android's external-volume directories
  (with `/storage` as a fallback), then passes the selected game folder to the
  native runtime so both use the same volume-specific root. On an SD card, put
  `SPEED2.EXE` and the game folders directly under `nfsu2` at the card root;
  grant all-files access and make sure the card is mounted. The application
  checks readable data folders and executable header/size; rejected paths and
  unreadable folders are reported in the `NFSU2` log.
  Android 11+ uses the all-files access settings screen; Android 10 requests
  read permission. Files remain external to the APK.
- Launcher icon from `ICONO/ICONO.png`, and a diagnostic status overlay.

## Build and inspect

Install Android SDK platform 35 and a JDK compatible with Android Gradle Plugin
8.7.3. `build.bat` detects the newest NDK and CMake already installed under
`ANDROID_SDK_ROOT`, `ANDROID_HOME`, `android/local.properties`, or the default
Windows SDK directory. Set `ANDROID_HOME` and `JAVA_HOME`, or configure the SDK
through Android Studio. From the repository root:

```bat
build.bat
```

By default this builds the ARM64 debug APK. To build the 32-bit APK instead:

```bat
set NFS_ABI=armeabi-v7a
build.bat
```

The result is:
`android/app/build/outputs/apk/debug/app-debug.apk`.

To install manually:

```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nfsu2.androidevolved/.LauncherActivity
adb logcat -s NFSU2
```

Copy files first with `scripts/copy_android_data.ps1 -GameRoot <installation>`.
To copy to a mounted volume, pass its path with
`-DevicePath '/storage/<volume>/nfsu2'`. Allow storage access when prompted.
The launcher selects the game language before starting the guest. The following
describes the historical bootstrap:
a dark teal screen and status
text, `Guest memory self-check passed`, `Game data readable`, `Bootstrap ready`
and `Presented frames` in logcat. Runtime 0.2.0 additionally attempts the actual
CRT entry and reports its first unsupported HLE import. No game assets are
packaged; generated code from the owner's copy is included in this local APK.
See [device test report](ANDROID_DEVICE_TEST.md) for version 0.1.0 real-device
verification. Version 0.2.0 includes locally generated code and has now passed
initial ARM64 runtime and Home/return checks; see ANDROID_RUNTIME.md for evidence.

If startup reports `D3DERR_NOTAVAILABLE` (`HRESULT 0x8876086A`), storage was
found but DXVK could not create the game's graphics device. Run **Probar
driver** in the launcher: its report lists required Vulkan features the
selected driver lacks. Try a system/updated GPU driver or a compatible custom
driver; if required features are absent from the device, the game renderer
cannot start on that GPU.

## Reference review

- https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP
  revision 08251847d48222aa6304b9ee521bfe8f430550fa
- https://github.com/codepdbh/nfsmw-android
  revision bc0fe33c4b4d9a5147f790122fb4af39f3734f1c

Both target Xbox 360 PowerPC/Xenos through ReXGlue. Their native renderer uses
Xenos registers and title-specific hooks; it cannot directly replace PC D3D9
or the x86 runtime. Useful reference patterns: optional 64-bit GPU addressing,
Vulkan 1.1 shader compatibility, BC texture CPU fallback, shader caches and
Android lifecycle. NFSU2 should generate SPIR-V for Vulkan 1.1 directly rather
than copy a shader version conversion specialized to a different library.
Their renderer code has not been copied into this MIT project. The standard
Gradle wrapper launchers/JAR were obtained from the MW reference (Gradle's
Apache-licensed tooling); the application and Vulkan bootstrap are new code.

## Original bootstrap roadmap (historical)

1. Portable x86 machine state and explicit guest callback dispatch, with
   complete integer/x87/MMX/SSE/MXCSR/flags state and serialized guest threads.
2. Connect guest memory to generated ADDR accesses and a relocatable PE loader.
   Audit unaligned accesses and host-pointer casts for ARM64 before execution.
3. Bind used Win32 imports to HLE; add guest handles and COM objects/vtables in
   guest memory. Never store native function/object pointers in guest uint32s.
4. D3D9 shim adapter: resources, locks/uploads, state, vertex declarations,
   draw commands and D3D9 bytecode-to-SPIR-V translation. Start with an isolated
   textured draw, then the intro/menu; use race traces to complete coverage.
5. DXT1/DXT5 CPU-to-RGBA fallback if BC formats are unsupported. Check depth,
   render-target and sampler capabilities; expose only supported D3D9 caps.
6. Android files/import, audio, input and game lifecycle, then menu and race.

The game's statically linked D3DX must also run correctly in the portable guest
runtime or be replaced at identified boundaries. Xbox 360 shader containers
and their shader tooling are not PC D3D9 shader inputs.
