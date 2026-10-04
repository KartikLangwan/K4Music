# K4 Music — UI, reliability and branding audit

Creator: **Kartik Langwan**  
Instagram: **K4RTIK.nvm** — https://www.instagram.com/K4RTIK.nvm/  
Website: https://kartiklangwan.vercel.app

## v2.1.0 — audit, stabilisation and redesign (5 October 2026)

A full pass over playback, navigation, audio, menus and the main screens, with the bugs fixed before the redesign. The source is now under local git (`dc89fa5` is the untouched v2.0.0.14 baseline), so every change can be reviewed with `git diff dc89fa5`.

### Defects found and fixed

| Area | Defect | Fix |
| --- | --- | --- |
| Navigation | The bottom bar's spacer was padded by the *player-aware* inset (mini player + 72 dp + system bar), pushing the bar up and leaving a blank band under it; the shell also assumed a 72 dp bar for a 64 dp bar. | The bar is now one root-level component sized from real constants and the system inset only. |
| Navigation | The player sheet's touch area covered the bar region, and the sheet state was recreated whenever the bar appeared or disappeared. | The bar is drawn above the player sheet and slides away as the player opens; sheet state no longer depends on navigation. |
| Navigation | Sub-screens added the status-bar inset twice (Scaffold top bar *and* list padding). | Scaffold passes only the bottom inset to content. |
| Repeat | The player button toggled only "repeat one"; "queue loop" (default **on**) was a separate hidden setting, so queues always wrapped; three controls disagreed. | One repeat mode (Off → All → One) shared by player, queue, notification and lock screen. |
| Next/previous | Next wrapped to the first song regardless of repeat; previous jumped to the last song. | Navigation follows the play order and wraps only with Repeat all; at the end the queue rewinds and pauses. |
| Shuffle | Shuffle was a one-off reorder with no state. | Persistent shuffle mode (ExoPlayer shuffle order, current song first). New queues start from the chosen song; "Play next" stays next while shuffled; the queue shows the real play order. |
| Queue | "Play next" / "Add to queue" replaced the whole queue whenever the player was idle — including after any playback error or sleep-timer stop. | Inserts never replace a non-empty queue; a finished queue plays the new song. |
| Queue | Tapping a song after an error did nothing (player idle, not re-prepared). | The player is prepared again. |
| Menus | Song-menu state (like, blacklist, album/artist links) was remembered without a song key, so a reused menu could show or act on the previous song. Queue "Remove" used an index captured when the menu opened. | State is keyed per song; removal re-verifies the target song. The player menu closes when the song changes. |
| Audio | `K4AudioEffects` (EQ presets, virtualizer) was dead code never attached to the player; "Equalizer" only opened an external app. | Real in-app audio effects (below). |
| Audio | Bass boost and reverb were leaked on service destroy; default bass strength was 5/1000 (inaudible); reverb was also sent as an aux effect it cannot be. | Effects live in one owner, are released on destroy and on disable, default strength 50 %, reverb is an insert effect. |
| Audio | Media3 1.11 assigns the audio session id asynchronously; `LoudnessEnhancer` could attach to session 0 (the global output mix) and was never re-created. | Effects attach on `onAudioSessionIdChanged`; session 0 is never used. |
| Performance | The whole player (mini + full) recomposed every 500 ms from a position read at the top, and kept polling while paused. | Position is a `State` read only by the seek bar and the mini progress line (draw phase); polling stops while paused. |
| Performance | Home's "Last interaction"/"For you" sources loaded the **entire** play history joined with songs on every play; the For You recommender ran on the main thread. | Bounded query (`events(limit)`), scoring on `Dispatchers.Default`. |
| Network/DB | The album page fetched and rewrote the album *inside* the album's own database flow; each write re-triggered the fetch — an endless request/write loop while an album was open. | Fetched once per visit, outside the flow. |
| Error states | Album and artist pages showed a permanent shimmer when the first load failed. | Error state with Retry. |
| Android Auto | The browser service read the database with `runBlocking` on the main thread and replaced the player's session callback (breaking like/repeat actions); an out-of-range queue id could crash. | Async `detach()`/`sendResult`, custom actions forwarded, bounds checked, scope cancelled on destroy. |
| Search | Clearing the field kept stale suggestions; suggestions duplicated history. | Cleared and de-duplicated; debounce shortened to 350 ms (one request per pause, not per keystroke). |
| Library | Songs showed "Your collection starts here" before the first query returned; Artists/Albums had no empty state; no "no matches" for the filter. | Loading skeleton, empty states with a Search action, no-matches state. |
| Settings | "Player layout" and "Show like button" did nothing; bass boost, reverb and equalizer were duplicated with the new audio screen. | Dead selector removed, like/previous-button settings honoured, one "Audio effects" entry. |
| Dead code | `k4/K4Theme.kt` (a second copy of the theme, which also crashed Android Lint's analyzer), unused tokens, glass helpers and strings. | Removed. |

### Audio effects

`Settings → Audio effects`, the equalizer button in the full player and the player menu open one screen. Each control maps to a real processing change; anything the device lacks is shown as "Not supported on this device" rather than a fake control.

- **Equalizer** — Android `Equalizer` on the player's session with the device's own bands and range (typically 5 bands, ±15 dB). Manual sliders (0.5 dB steps) with a response curve, Reset, Save preset and deletable user presets.
- **Presets** — Flat, Vocal, Bass, Bass Boost, Treble, Rock, Pop, Classical, Electronic. Curves are defined at 60 Hz–14 kHz and interpolated on a log-frequency axis onto whatever bands the device reports, so a preset means the same on 5- and 10-band equalizers.
- **Bass boost** — Android `BassBoost` with strength.
- **3D & Surround** — Android `Virtualizer`, forcing binaural (3D, headphones) or transaural (Surround, speakers) mode where the device supports it, with depth. Modes the device reports as unsupported are disabled.
- **ATD wide stage** — the requested "ATD" has no Android API, so it is implemented as K4 Music's own mid/side stereo widener inside the Media3 audio pipeline (`StereoWidener`): side signal raised, mid trimmed to hold loudness, soft knee instead of hard clipping, bypassed for mono. It works on every device and output.
- **Reverb** — Android `PresetReverb` presets.

Effects exist only while enabled (a disabled effect costs nothing and does not fight a system-wide equalizer), are released on disable and service destroy, and reattach when the session changes. Every platform call is guarded so unsupported devices degrade instead of crashing. The system equalizer remains available from the screen.

### Redesign

- **Shell**: persistent bottom bar (Home, Discover, Search, Library) anchored at the bottom with the system inset; the mini player docks directly above it; tapping a destination always returns to its root; wide windows keep the navigation rail.
- **Mini player**: artwork, title, artist, like, play/pause, next (previous optional), progress line, tap to open, swipe-to-close setting kept; 48 dp touch targets.
- **Full player**: collapse / now playing + album / more; large artwork; title, artists, like; seek bar with elapsed and total/remaining; shuffle, previous, play/pause (buffering indicator), next, repeat with state labels; Lyrics, Up next and Audio effects in the queue bar.
- **Design system**: near-black purple-tinted dark palette, brighter violet accent with a contrast-checked `onAccent`, flat tonal surfaces (no shadowed glass), new menu sheet with handle and larger rows, new cards, tiles, shortcuts, skeletons and empty/error states. Fresh installs default to dark mode and the brand purple; saved appearance choices are untouched.
- **Home**: greeting, quick-access grid (Favourites, History, Offline, Playlists), Recently played, Quick picks with an honest origin line ("Based on …", "Picked from your listening", "Popular right now"), a shelf of more picks, genres and related albums/artists/playlists.
- **Discover**: featured trending song, trending list, new releases, moods, Indian and global genres; skeleton and retry states.
- **Search**: pill search bar; history and suggestions.
- **Library**: chips for Songs, Playlists, Artists, Albums, Local and Offline, with empty and loading states.

### Validation for v2.1.0

Run on 5 October 2026 from a clean tree (`gradlew clean`, then `assembleDebug`, then `scripts/verify-release.ps1 -Offline`):

- Clean debug build: passed.
- Python updater tests: 4/4 passed.
- Android unit tests: **72/72 passed** (64 existing + new tests for queue navigation in shuffle/repeat order, preset-to-band mapping, the soft clipper and the stereo widener).
- Android Lint: **0 errors**, 214 warnings (185 missing translations for new English strings, 23 unused resources, the same count as before, and 6 existing suggestions).
- Release build (R8, resource shrinking) and lint-vital: passed.
- APK: `K4Music-v2.1.0-RELEASE.apk`, version code 2010000, 36,377,234 bytes, APK Signature Scheme v2 verified (existing Android debug certificate). arm64-v8a and x86_64 QuickJS and Python runtimes are packaged.
- SHA256: `B85BAFE2278758F832C7EAF05D5F8786C2EFB20E0E5B7D96E5DBF8D5D5A4DF07`.

### Remaining limitations

- No emulator or device run was performed (at the owner's request). Playback, background/lock-screen behaviour, audio-effect audibility, headset/focus handling and the new layouts are verified by compilation, unit tests, lint and code review only, not on hardware.
- Equalizer, bass boost, virtualizer and reverb strength and availability depend on each phone's audio-effect implementation; some OEM builds ignore forced virtualizer modes. ATD wide stage is in-app and device-independent, but only affects stereo tracks.
- Reordering the queue by drag is disabled while shuffle is on, because the shown order is the shuffle order.
- New strings ship in English; other locales fall back to English for them.


## Scope and visual changes

The merged workspace is the working app. The original projects remain in sibling folders.
The latest pass addresses the user's reported intermittent playback failures alongside the Home, Discover, Settings and player-control redesign. Device runtime verification remains with the user, as requested.

- New Home: greeting, featured song, Favorites/History/Offline shortcuts, recommendations and album/artist/playlist shelves.
- New Discover: introduction, featured card, rounded mood grid, new releases and trending tracks.
- Adaptive navigation: four phone destinations with collection tabs; a rail on larger windows.
- New shared music cards and song rows with bounded titles, durations and an active-song highlight.
- Updated full player and mini player controls, readable headers, explicit playback labels and larger touch targets.
- Updated settings spacing and creator card, website/Instagram actions, diagnostics, empty states and loading/retry screens.
- Search retains the existing query/results flow, with the updated shell, shared rows and pagination recovery.
- Appearance preferences continue to control the app palette. Light/dark themes share Material colours with the existing player.

## Identified defects addressed

### Final polish pass — 4 October 2026, v1.5.6

- Home/Discover use compact artwork cards with theme-aware text instead of white text on a light gradient. Shortcuts are shorter glass rows. No full-screen blur or new rendering dependency was added.
- Home and Discover reserve space for the mini player so the final songs remain reachable. Player and navigation docking use consistent bounds, with less empty space.
- The expanded player has one clear footer for Lyrics, Up next and playback settings. Short landscape windows can scroll the controls; the artwork remains separate.
- Buffering has a visible indicator, playback failures have a labelled retry button, and the seek control supports accessibility progress actions.
- An empty queue no longer indexes song -1. Recommendation requests are bounded, cancellation-aware, deduplicated and expose retry instead of a permanent skeleton. Local and Saavn IDs bypass YouTube recommendation calls.
- Queue actions use window indices rather than period indices. Cancelling a scroll no longer shuffles the queue through a completion callback.
- Previous/next navigation performs a bounded scan. A queue where every song is filtered cannot spin forever; automatic filtering pauses with a visible explanation.
- Shuffle tolerates an empty queue, stale play indices are clamped, and playback statistics tolerate an expired timeline window.
- Player state follows the current service connection. Listener registration no longer repeats on each playback-progress recomposition, and the activity listener is keyed to the current sheet state.
- The bass-boost slider now maps 0–1 to Android's 0–1000 strength scale and clamps stored values.
- Saavn sharing uses song metadata and the K4 Music website rather than a malformed YouTube URL. Unsupported YouTube actions are hidden for Saavn; unavailable external apps produce a message.
- Unknown-duration playback no longer displays invalid remaining time. New installs default to the static seek bar to reduce continuous animation work; saved choices are respected.
- Empty terminal pagination responses clear continuation while preserving loaded songs.
- Python installer failures and nonzero exit codes reach the Android error handler, preventing false update-success messages.
- QuickJS is a generated variant source and a packaging dependency. The old assemble-time copy ran after packaging and could ship a stale executable; fresh binaries now enter the same build's APK.

The audit covered the merged app's startup/lifecycle, shared UI, player/queue, source resolution, downloads/cache, search/pagination, database/backup, permissions, external actions and branding. Static review and targeted regression tests do not replace device validation of every feature.

### Startup and lifecycle

- UI no longer waits for optional wallpaper colours or media permissions before rendering.
- Dynamic-colour setup has a fallback when Monet is unavailable.
- Database opening runs away from the main thread and presents a retry/diagnostic screen on failure.
- Database startup failures no longer delete the user's library.
- Database construction is lazy so a failed first attempt does not poison singleton initialisation.
- Service bindings are tracked, released safely and rebound when Android reports a dead binding.
- Binder waits have a timeout; startup errors are surfaced instead of waiting indefinitely.
- Local music permission is requested in the device-library flow; online music can open without it.
- MediaStore scans follow the visible lifecycle and handle revoked permissions.
- Notification operations handle failures inside the posted callback; foreground failures stop the affected service.
- Corrupt typed preference values fall back to defaults, and preference writes preserve the latest action.
- Reloading internals keeps the shared Room connection, avoiding stale DAO references.

### Playback, offline and network

- Seek progress is bounded and handles unknown/zero durations.
- Player sheet anchors, drag values and tiny-window insets are clamped safely.
- Range recovery removes the rejected Range header, closes the old source and reopens with an unknown length.
- Fallback data sources now read, close and report the actual selected stream.
- Resolver subranges no longer add the seek position twice.
- Retry handling closes failed sources and does not sleep after the final attempt.
- Direct Innertube streams remain available when the optional extractor cannot resolve metadata.
- Empty format lists return no format rather than throwing.
- Likes follow only the current song; loading favourite status no longer writes to the database.
- Offline lists observe cache completion/eviction and detach cache listeners on cancellation.
- The merged standalone downloader serialises writes, confines filenames to its folder, rejects empty/truncated payloads and requires a completed rename before reporting success.
- UI network requests have a bounded load time and preserve navigation cancellation.
- Pagination consumes the next page's continuation, drops keyless entries, deduplicates items and exposes retry after a failed load.
- Invalid cached Home entries and empty completed responses no longer leave a permanent loading skeleton.
- Recent recommendations use the newest history entries.
- Filtered song lists play in the order shown.

### Data and presentation

- Backup errors are surfaced; transfers prevent concurrent backup/restore actions.
- Restore stages and validates the backup through Room before replacing the live database.
- Restore checks database integrity and migrations, checkpoints writes, retains data-before-restore.db, and uses an atomic file replacement.
- Crash-log reads tolerate invalid limits.
- Shared item content updates with new metadata instead of retaining its first remembered lambda.
- Playlist artwork queries are keyed by playlist identity.
- Disabled header/icon controls now stay disabled.
- Resource duplication, version-specific splash resources, plural forms, translation-format mismatches and SDK-property escaping were corrected.

## Rebranding

Public app text, launcher build labels, translated app mentions, creator/version credits, the startup screen, navigation header, About screen, backup filenames and store/project descriptions use **K4 Music** and **Kartik Langwan**.
The creator's Instagram and website are linked from About and documented in project/store metadata.

Placeholder update/contact links to a nonexistent K4 Music GitHub repository were removed. Legacy update workers are retained for compatibility and their scheduled work is cancelled; no unverified update repository is advertised.

Technical package names, application IDs, theme identifiers and persistent storage keys remain stable to preserve compatibility.
ViTune appears in the explicit upstream attribution. Original licences and contributor credits are retained.
Old upstream screenshots were removed from the README because they do not represent the redesigned interface.

## Previous validation — v2.0.0.14

The final source passed `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleRelease` in **revamp-2.0.0.14-final.log**. All **64 Android unit tests** and **4 Python tests** passed. Lint found **0 errors and 168 warnings**: 137 missing translations, 23 unused resources and 8 existing API/dependency/metadata suggestions. New UI labels use the English fallback where translations are missing. Release lint also passed.

The final APK's manifest metadata is version **2.0.0.14**, code **2000014**, application ID **com.k4music.app**. It passed APK Signature Scheme v2 verification with the existing Android debug certificate. Both arm64-v8a and x86_64 QuickJS files exactly match the generated ELF binaries, both Python runtimes are present, and the compiled Python source contains the updater failure handling and bounded extractor options.

Artifact: `K4Music-v2.0.0.14-RELEASE.apk`, **36,322,962 bytes / 34.64 MiB**. SHA256: `8334805f44e6da252fd4c2fe2c7cc623741ed476ef45bec42ddbf6f8560c22d1`. The companion `.apk.sha256` file was exported by `scripts/verify-release.ps1`.

Evidence: `revamp-2.0.0.14-final.log`, `revamp-2.0.0.14-python.log`, `revamp-2.0.0.14-verification.log`, `app/build/test-results/testDebugUnitTest/` and `app/build/reports/lint-results-debug.html`.

No LDPlayer/emulator/device playback or visual runtime validation was performed. The unit tests cover extraction parsing/headers, bounded retry decisions, cyclic error causes, read-error callbacks and existing regression paths; they do not reproduce every live-provider failure. Public distribution still requires a private release signing key and device acceptance checks. Earlier releases and their checksums remain in the workspace.

## Earlier validation (v1.5.6)

Previous release validation for v1.5.6 succeeded: **54 Android unit tests passed**, **3 Python updater tests passed**, and Android lint reported **0 errors and 138 warnings**. Warnings comprise 108 missing-translation reports, 22 unused-resource reports and 8 existing API/dependency/metadata suggestions. Release lint checks also passed.

The fresh release APK passed APK Signature Scheme v2 verification. Its certificate is the project's existing Android debug key. The packaged arm64-v8a and x86_64 QuickJS executables exactly match this build's generated binaries; both Python runtimes are present, and the updated Python failure handling was confirmed inside the APK's compiled source archive.

Delivered artifact: `K4Music-v1.5.6-RELEASE.apk` (version code **1506**, **36,302,446 bytes / 34.62 MiB**, application ID `com.k4music.app`). The build output is `app/build/outputs/apk/release/app-release.apk`.

SHA256: `019d1923910d4e95e7bf47c6c8a90ac8a430df0be9ec844760ce4a69b56efd64`.

The repeatable workflow is `scripts/verify-release.ps1`: it runs regression tests and lint, builds the release, verifies the signature and runtime entries, and exports an APK plus checksum. Evidence is retained in `release-verification.log`, `apk-verification.log`, `app/build/test-results/testDebugUnitTest/` and `app/build/reports/lint-results-debug.html`.

LDPlayer was not used for this revision's runtime validation, as requested. No emulator or device installation is part of the handoff.
Compile checks, unit tests and lint do not establish that every Android/device/network path is bug-free.

## User runtime checks

1. Fresh launch and relaunch without device-audio permission; navigate Home, Discover, Search, Library and Offline.
2. Play a known working song; expand/collapse the player, seek, skip, like/unlike, and resume after backgrounding.
3. Disable connectivity while loading a shelf; check retry and fully cached offline playback.
4. Complete a download while the Offline screen is visible; verify it appears and still plays offline.
5. Grant/revoke local-audio permission, rotate the screen and open the keyboard on search.
6. Check light/dark appearance, large text, long song titles and the creator links.
7. Export a backup; verify an invalid restore is rejected and a valid restore preserves the recovery file.

Local release APKs currently use the project's configured debug signing key. Configure a private release keystore before public distribution.
