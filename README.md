# Vidsource 🎞️

**Vidsource** is an Android app (phone, tablet & **Android TV**) that turns your favorite
streaming websites into switchable TV channels. Add website links manually in Settings,
switch between them from a favicon “profile” strip (like a channel list), and play detected
videos in a **native player** with proper remote / double-tap seeking.

Demo source shipped by default: [https://cinejoy.pk/](https://cinejoy.pk/)

> **Branch note:** this work lives on `arena/afadddd0-movies-and-tv` and is intentionally
> **not merged** — review / merge when you are ready.

## Features

- **Multi-source browser** – each source is a website link you add by hand (name + URL).
  Sources persist locally and can be edited, reordered or deleted at any time.
- **Channel-style source switching** – a horizontal strip of website favicons acts as the
  profile switcher. Tap one to change the channel; on TV use **CH+ / CH-** (or media
  next/previous) to zap between sources.
- **Native player (Media3/ExoPlayer)** – when a video URL is detected (HLS `.m3u8`, DASH
  `.mpd`, `.mp4`, …) playback opens in the built-in native player:
  - **Double-tap left / right half** of the screen to seek **−10 s / +10 s** (with a seek pill).
  - **D-pad ◀ / ▶** on the remote seek −10 s / +10 s (hold to keep seeking).
  - **OK / D-pad center** play-pause, **▲** show controls, **▼** hide controls.
  - Rewind / Fast-forward remote keys seek as well.
  - CH+ / CH- switch source without leaving the player.
- **Android TV layout + D-pad support** (TCL & other Android TV devices):
  - Leanback launcher entry + TV banner.
  - Virtual **pointer cursor**: move it with the D-pad, OK clicks links — full web page
    navigation without touch. Push past the top edge to reach the channel strip.
  - REW/FF keys scroll the page, MENU opens settings, everything is focus-highlighted.
- **Video URL extractor** – injected JavaScript watches `<video>` elements, `play()` calls,
  fetch/XHR and player libraries; direct media URLs open natively, exotic (blob/DRM)
  streams keep playing in the page (with fullscreen support).
- **Website icons as profiles** – favicons are captured and cached automatically
  (with a generated letter-avatar fallback).

## Remote mapping (Android TV)

| Key | Browser screen | Native player |
| --- | --- | --- |
| ◀ ▶ ▲ ▼ | Move virtual pointer (up-at-top-edge → channel strip) | ◀ ▶ seek ±10 s · ▲ show controls · ▼ hide |
| OK / Center | Click link | Play / pause (or focused button) |
| CH+ / CH− | Previous / next source | Previous / next source |
| Rewind / FF | Scroll page | Seek −10 s / +10 s |
| MENU / Guide | Settings | Choose source list |
| Back | Page history / exit | Hide controls / exit |

Touch phones: tap the favicon strip to switch sources; in the player **double-tap** the
left/right screen half to seek, single tap toggles controls.

## Project structure

```
app/src/main/java/com/vidsource/app/
├── VidsourceApp.kt              # App container (repo + favicon store)
├── data/                        # SourceProfile, SourceRepository (local JSON store)
├── util/                        # TV detection, immersive UI, favicon cache
└── ui/
    ├── MainActivity.kt          # Browser + source switcher + video detection
    ├── SourceSwitcherAdapter.kt # Favicon "channel" strip
    ├── browser/DpadPointerView.kt # Virtual D-pad cursor for web pages
    ├── player/PlayerActivity.kt # Native Media3 player (double-tap / D-pad seek)
    └── settings/SettingsActivity.kt # Add / edit / delete source links
app/src/main/assets/video_hooks.js   # In-page video URL extractor
app/src/main/res/layout-television/  # TV-specific layouts
```

## Build

Requirements: **Android Studio** (Koala or newer), **JDK 17**, Android SDK 34.

```bash
# From the repo root
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # Install on a connected device / TV (adb)
```

Or open the project in Android Studio and press Run. First Gradle sync downloads
Gradle 8.7, the Android Gradle Plugin 8.5.2 and dependencies.

### Install on an Android TV (e.g. TCL)

```bash
adb connect <tv-ip>
./gradlew installDebug
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech

- Kotlin, single `app` module, minSdk 21 (Android 5.0 — covers Android TV), target/compile SDK 34
- [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3/exoplayer) (HLS, DASH, progressive) + `media3-ui` TV-friendly controls
- Material 3 (dark cinema theme), RecyclerView, coroutines
- `WebView` browser layer with a JS bridge for stream URL extraction
- No accounts, no tracking — sources are stored on-device only

## Notes & limitations

- `android:usesCleartextTraffic="true"` is enabled because many stream hosts still use
  `http://` segments; tighten this in `AndroidManifest.xml` if you only use HTTPS sources.
- Some sites serve DRM / MSE `blob:` streams — those can’t be handed to ExoPlayer, so they
  keep playing inside the page (D-pad seek falls back to page keys there).
- Add more sources via **Settings → Add source** (name + link). The demo CineJoy profile
  is pre-seeded and can be edited or removed like any other.
