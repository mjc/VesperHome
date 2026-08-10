# Vesper Home

> [!WARNING]
> This project is a work in progress and is tailored to a specific Android TV setup. Compatibility and behavior may vary across devices and firmware.

Vesper Home is a native Kotlin launcher for Android TV. It uses XML Views, View Binding, RecyclerView, Room, DataStore, and explicit D-pad focus handling without a Flutter or Compose runtime.

## Features

- Unified catalog for TV and sideloaded applications.
- Favorites, custom categories, spacers, row and grid layouts, sorting, hiding, and manual ordering.
- Native banners, icon fallbacks, and custom application banners.
- Jellyfin random tracks, playlists, and album playback with discovery, Quick Connect, artwork, and launcher controls.
- Configurable wallpapers, day and night scheduling, themes, status bar, and OLED clock screensaver.
- TV input selection, notification panel and popups, brightness scheduling, and Home-button accessibility handling.
- Timestamped backup, restore, import, sharing, and custom-asset recovery.
- English and Spanish localization.

## Build

The project requires JDK 17 and the Android SDK. Build with the repository Gradle wrapper:

```shell
./gradlew format
./gradlew lint
./gradlew check
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` with application ID `com.sergioasenjo.vesperhome.debug`.

## Default Launcher

Open Vesper Home settings and select the default-launcher action to open the Android Home application selector. Devices that prevent changing the Home application can optionally use the built-in accessibility-based Home Button Fix.

Changing or disabling a device's existing launcher can make its interface inaccessible. Do not disable the existing Home application unless you understand the device-specific recovery procedure.

## Credits

Vesper Home is derived from [FLauncher](https://gitlab.com/flauncher/flauncher) by [etienn01](https://github.com/etienn01), the [FLauncher fork](https://github.com/osrosal/flauncher) by [osrosal](https://github.com/osrosal), and subsequent work by [LeanBitLab](https://github.com/LeanBitLab).

This renamed native implementation contains substantial modifications made through August 10, 2026.

## License

Vesper Home is distributed under the GNU General Public License v3.0. See [`LICENSE`](LICENSE).
