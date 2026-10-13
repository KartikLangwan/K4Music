<div align="center">
    <img src="./app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="K4 Music icon"/>
    <h1>K4 Music</h1>
    <p><b>Clean. Fast. Yours.</b><br/>A free, ad-free music player for Android by Kartik Langwan</p>

<a href="https://github.com/KartikLangwan/K4Music/releases/download/v2.12.14/K4Music-v2.12.14-RELEASE.apk"><img src="https://img.shields.io/badge/Download%20APK-v2.12.14-7C4DFF?style=for-the-badge&logo=android&logoColor=white" alt="Download APK v2.12.14"/></a>
<a href="https://github.com/KartikLangwan/K4Music/releases"><img src="https://img.shields.io/badge/All%20releases-555?style=for-the-badge&logo=github" alt="All releases"/></a>

<p>
<img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+"/>
<img src="https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20x86__64-informational" alt="ABIs"/>
<img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin, Jetpack Compose"/>
<img src="https://img.shields.io/badge/ads-none-success" alt="No ads"/>
</p>

<p><a href="https://www.instagram.com/K4RTIK.nvm/">Instagram: K4RTIK.nvm</a> · <a href="https://kartiklangwan.vercel.app">kartiklangwan.vercel.app</a></p>
</div>

---

## Download

| | |
| --- | --- |
| **Latest** | [**K4Music-v2.12.14-RELEASE.apk**](https://github.com/KartikLangwan/K4Music/releases/download/v2.12.14/K4Music-v2.12.14-RELEASE.apk) |
| **Checksum** | [SHA256](https://github.com/KartikLangwan/K4Music/releases/download/v2.12.14/K4Music-v2.12.14-RELEASE.apk.sha256) · `7CC80335AEB36B9B36E02760235D88761C9288FF28535FD9BBC07D9C44DA42BB` |
| **Requires** | Android 7.0 (API 24) or newer, 64-bit phone (arm64-v8a) or x86_64 |
| **Size** | about 35 MB |

**Install:** download the APK on your phone, open it, and allow *Install unknown apps* for your browser or file manager when Android asks. Updating from an older K4 Music build signed with a different key? Uninstall that build first.

> The repository is private, so the download links work for signed-in collaborators only.

## What's new in 2.12.14

- **Songs no longer stop on errors.** Auto-skip is gone. A failing stream is retried within milliseconds, and if YouTube cannot play a song, K4 Music plays **the same song from JioSaavn** instead. This is *Smart fallback* (Settings → Player, on by default).
- **Fewer mid-song stalls.** One failing song no longer forces every other song to reload its stream.
- **Faster start.** Your saved queue loads when you press play, not at launch, and the YouTube extractor warms up in the background.
- **Living player.** A soft glow in the song's own colours drifts behind the artwork while music plays and rests when paused.
- **New Settings home** with grouped sections, a status card and the FAQ.

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

## Build from source

Needs JDK 21+ and the Android SDK.

```
gradlew :app:assembleRelease
```

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
