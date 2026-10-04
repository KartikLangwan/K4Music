<div align="center">
    <img src="./app/src/main/ic_launcher-playstore.png" width="128" height="128" style="display: block; margin: 0 auto"/>
    <h1>K4 Music</h1>
    <p>A music app by Kartik Langwan</p>
    <p><a href="https://www.instagram.com/K4RTIK.nvm/">Instagram: K4RTIK.nvm</a> · <a href="https://kartiklangwan.vercel.app">kartiklangwan.vercel.app</a></p>
</div>

---

Version **2.1.0** is a stability and redesign release: one consistent repeat/shuffle model, queue-safe "Play next", a persistent bottom bar with the mini player docked above it, a rebuilt full player, real audio effects (equalizer with presets and manual bands, bass boost, 3D/surround, ATD wide stage, reverb) and a new dark-purple design. See [AUDIT.md](AUDIT.md) for every fix and its validation.

## Features

- Play (almost) any song or video from YouTube Music
- Play music from your device
- Play songs in the background
- Cache song for offline playback
- Search for songs, albums, artists videos and playlists
- Discover new songs by mood/genre
- Import playlists from YouTube
- Fetch, display and edit songs lyrics or synchronized lyrics
- Manage your playlists locally or synchronize with the cloud
- Highly customizable (dynamic theme, Material You, ...)
- Normalize audio for the perfect listening experience
- Listen from your car using Android Auto
- Open YouTube/YouTube Music links (`watch`, `playlist`, `channel`, etc.) with K4 Music
- Ridiculously lightweight APK

## Installation

Build the release APK (needs JDK 21+ and the Android SDK):

```
gradlew :app:assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It supports Android 7.0+
(API 24) on arm64-v8a and x86_64 devices. Local release builds currently use the debug
signing key; configure a private release keystore before distributing publicly.

For the repeatable verification workflow, run `scripts/verify-release.ps1`
with `-PythonCommand` pointing to a Python 3 interpreter and, if needed,
`-GradleCommand` pointing to Gradle 9.8.0. Use `-Offline` when dependencies are
already cached. The script runs regression tests and lint, builds the release,
checks its signature and packaged runtimes, and exports the APK with a SHA256
checksum. See [AUDIT.md](AUDIT.md) for fixes and validation limits.

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
