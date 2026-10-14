<div align="center">
    <img src="./app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="K4 Music icon"/>
    <h1>K4 Music</h1>
    <p><b>Your music. Every screen.</b><br/>A free, ad-free music player for Android and Windows by Kartik Langwan</p>

<a href="https://k4music.vercel.app/download"><img src="https://img.shields.io/badge/Android-Download%20APK-7C4DFF?style=for-the-badge&logo=android&logoColor=white" alt="Download for Android"/></a>
<a href="https://k4music.vercel.app/download/windows"><img src="https://img.shields.io/badge/Windows-Download%20installer-7C4DFF?style=for-the-badge&logo=windows&logoColor=white" alt="Download for Windows"/></a>
<a href="https://github.com/KartikLangwan/K4Music/releases"><img src="https://img.shields.io/badge/All%20releases-555?style=for-the-badge&logo=github" alt="All releases"/></a>

<p>
<img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+"/>
<img src="https://img.shields.io/badge/Windows-10%20%7C%2011%20(64--bit)-0078D4?logo=windows&logoColor=white" alt="Windows 10 and 11"/>
<img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin, Compose"/>
<img src="https://img.shields.io/badge/ads-none-success" alt="No ads"/>
</p>

<p><a href="https://k4music.vercel.app">k4music.vercel.app</a> · <a href="https://www.instagram.com/K4RTIK.nvm/">Instagram: K4RTIK.nvm</a> · <a href="https://kartiklangwan.vercel.app">kartiklangwan.vercel.app</a></p>
</div>

---

## Download

Downloads are in this repository's [**Releases**](https://github.com/KartikLangwan/K4Music/releases) and on [k4music.vercel.app](https://k4music.vercel.app).

| | Android | Windows |
| --- | --- | --- |
| **File** | [K4Music-v2.12.14-RELEASE.apk](https://github.com/KartikLangwan/K4Music/releases/download/v2.12.14/K4Music-v2.12.14-RELEASE.apk) | [K4Music-Setup-2.12.14.exe](https://github.com/KartikLangwan/K4Music/releases/download/v2.12.14/K4Music-Setup-2.12.14.exe) |
| **Requires** | Android 7.0+, arm64-v8a or x86_64 | Windows 10 / 11, 64-bit |
| **Size** | 34.9 MB | 116.3 MB |
| **SHA-256** | `F832E9EE6A5AD13BE0889D22F75650653088EB796742BB3C3DAF9C75CB2C012C` | `E8C6A7E0676121E5B75D5F502DCD4F37FE00BA1BFDF9874B37C1A6C2ECDE5C65` |

**Android:** open the APK on your phone and allow *Install unknown apps* for your browser when Android asks. Updating from a build signed with a different key? Uninstall that build first.

**Windows:** run the installer (no admin rights needed). It isn't code-signed yet, so on *Windows protected your PC* click **More info → Run anyway**. Later updates: Settings → Check for updates → **Update now**, or run a newer installer; it closes K4 Music, updates it and keeps your library.

## What's new in 2.12.14

- **K4 Music for Windows.** The whole app on PC: glass panels over the song's colours, a vinyl full player, media keys and Bluetooth headphone buttons, taskbar controls, audio output switching (Bluetooth, headphones, speakers), a Discord status, autoplay radio, resizable panels, full screen and in-app updates.
- **A new look on Android**, matching the PC: logo header, a soft violet glow behind every screen, round covers, a capsule mini player with a progress ring and the K4 gradient play buttons.
- **Songs keep playing.** If YouTube can't serve a song, the same song plays from JioSaavn (*Smart fallback*). When YouTube is limiting the whole network for a while, K4 Music now waits it out instead of retrying every song.
- **Faster:** quicker downloads, the next song preloaded, the saved queue loaded only when you press play.
- **Double-tap the artwork** to seek 10 seconds back or forward.

Every fix and how it was verified: [AUDIT.md](AUDIT.md).

## Features

- 🎵 **Two catalogues, one player:** YouTube Music first, JioSaavn as a second source and automatic fallback
- ⬇️ **Downloads** for offline listening, with streaming quality from *Data saver* to *High*
- 📥 **Playlist import** from Spotify and YouTube Music links
- 🎚️ **Real audio effects:** equalizer with presets and manual bands, bass boost, 3D/surround, wide stage, reverb, loudness normalization
- 🔁 Consistent repeat and shuffle, queue-safe *Play next*, persistent queue
- 🔍 Search songs, albums, artists, videos and playlists; discover by mood and genre
- 🎤 Lyrics, including synchronized lyrics
- 📱 Local music from your device, background playback, Android Auto
- 🎨 Dynamic theme and Material You colours, dark purple design
- 🔗 Opens YouTube / YouTube Music links (`watch`, `playlist`, `channel`, …)
- 🚫 No ads, no account needed, lightweight

### On Windows

- 🎧 Pick the audio output (Bluetooth, headphones, speakers), even mid-song
- ⌨️ Media keys and Bluetooth headphone buttons work in the background; Previous / Play-Pause / Next under the taskbar thumbnail
- 💬 Discord status: *Playing K4 Music* with the song and cover, and an icon next to your name in voice channels
- 🪟 Resizable, hideable panels, navigation left or bottom, full screen (F11), system tray
- 🔄 In-app updates from this repository's releases

## Build from source

Needs JDK 21+ and the Android SDK.

```
gradlew :app:assembleRelease
```

Windows app (Compose Desktop). Put the native tools in `windows/downloads/` first: `mpv/libmpv-2.dll`, `yt-dlp_win.zip`, `qjs-windows-x86_64.exe` and a portable Inno Setup in `innosetup/`. Then:

```
gradlew -p windows packageInstaller
```

The installer lands in `windows/build/installer/`.

The APK lands in `app/build/outputs/apk/release/`. Local release builds use the debug signing key; configure a private release keystore before distributing publicly.

For the repeatable verification workflow, run `scripts/verify-release.ps1` with `-PythonCommand` pointing to a Python 3 interpreter and, if needed, `-GradleCommand` pointing to Gradle 9.8.0. Use `-Offline` when dependencies are already cached. The script runs regression tests and lint, builds the release, checks its signature and packaged runtimes, and exports the APK with a SHA256 checksum.

## Origin

K4 Music is a merge of two codebases: [ViTune](https://github.com/25huizengek1/ViTune)
(base: player, providers, UI) and K4 Music (JioSaavn catalogue, taste engine,
crash-safe downloads, K4 theme). Upstream licenses and attributions are kept
in `LICENSE` and Acknowledgments below.

## Acknowledgments

- [**YouTube-Internal-Clients**](https://github.com/zerodytrash/YouTube-Internal-Clients): A Python
  script that discovers hidden YouTube API clients. Just a research project.
- [**ionicons**](https://github.com/ionic-team/ionicons): Premium hand-crafted icons built by Ionic,
  for Ionic apps and web apps everywhere.
- [**Flaticon: Ilham Fitrotul Hayat**](https://www.flaticon.com/authors/ilham-fitrotul-hayat): the
  app's logo uses a music note icon.

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed by, or in any
way associated with YouTube, Google LLC or any of its affiliates and subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used in this project
are owned by the respective owners.
