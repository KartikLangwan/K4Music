# K4 Audio for Windows

Kotlin + Compose Desktop. Plays through libmpv, gets YouTube streams from yt-dlp (with QuickJS for its JS challenge) and falls back to JioSaavn.

```
gradlew -p windows test              # unit tests (native playback tests are skipped without libmpv)
gradlew -p windows run               # development run, data in windows/build/dev-data
gradlew -p windows packageInstaller  # K4Audio-Setup-<version>.exe (needs the native tools below)
```

## Native tools (Native tools)

They are not in git. Put them in `windows/downloads/`; `verifyNativeTools` checks every SHA-256 below and fails the build on a mismatch.

| File | Where from | SHA-256 |
| --- | --- | --- |
| `yt-dlp_win.zip` | yt-dlp release `2026.08.19`, asset `yt-dlp_win.zip` | `30b4c14aafab6082becff7881e41b76df46dc43ea7633479410a91e29da492bf` |
| `qjs-windows-x86_64.exe` | QuickJS (qjs) Windows x86-64 build | `2aeabf0092c3262d6b2609824418f7dd7ed1f1df939f73b2b15645230cac0d77` |
| `mpv/libmpv-2.dll` | `mpv-dev-x86_64-20261013-git-9d144e57b6.7z` from the mpv-player-windows libmpv builds | `5080e00cd114f3dfc0eb87f35068fec70afc8bfd6fc29dca4b79c874a42485ee` |
| `innosetup/ISCC.exe` | portable Inno Setup 7.1.0 (only for `packageInstaller`) | not pinned |

The installed app refreshes yt-dlp by itself once a week (`Maintenance.updateYtDlpIfDue`); the pinned zip is only the version that ships.

To bump a tool, replace the file, update the hash in `build.gradle.kts` and in this table.
