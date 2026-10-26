# Seismo Revamped

A focused Android 16 refresh of the Seismo accelerometer app and live wallpaper, based on the original Seismo projects by Ari Wilson in [arimwilson/code](https://github.com/arimwilson/code/tree/master/Seismo).

The standalone app and wallpaper are separate APKs. Both support Android 5.0 (API 21) and newer and target Android 16 (API 36).

## Features

- Live accelerometer trace with X, Y, Z, or combined vector magnitude.
- Gravity removal that centers motion around zero, with a raw mode that keeps the sensor's gravity offset.
- Auto fit that scales the trace to recent readings without changing the recorded values.
- Fixed graph ranges from ±0.10 g to ±10 g for closer zoom.
- Configurable sample interval from 5 ms to 100 ms (nominally up to 200 Hz), subject to each device's sensor limits.
- 128-point FFT spectrum view with frequency and amplitude axes.
- Standalone controls for pause, save, and CSV export.
- Wallpaper controls for trace and background color, range, sample rate, axis, gravity removal, auto fit, and FFT view.

The FFT uses the latest 128 samples and a Hann window. Its frequency resolution and upper frequency depend on the selected sample interval and the device accelerometer.

## Build

Use JDK 17 and Android SDK Platform 36 with Build Tools 36.0.0.

With Gradle:

```sh
./gradlew :Seismo:assembleDebug :SeismoWallpaper:assembleDebug
```

Or build debug-signed APKs with the included SDK script:

```sh
ANDROID_SDK_ROOT=/path/to/android-sdk ./scripts/build-apks.sh
```

The APKs are written to `artifacts/`. Debug builds for sideloading are also included here:

- [Standalone Seismo APK](artifacts/Seismo-debug.apk)
- [Seismo live wallpaper APK](artifacts/SeismoWallpaper-debug.apk)

They use a local debug key for development. A release or Play Store build needs a release signing key.

## Upstream attribution

The Java application and wallpaper code started from the Seismo directories in [arimwilson/code](https://github.com/arimwilson/code). This focused repository keeps the upstream project attribution. No license file was present in the upstream checkout used for this refresh; check with the original author before redistributing under a new license.
