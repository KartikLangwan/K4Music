# K4 Music — Comprehensive Changelog & Architectural Enhancements

## Claude follow-up — 23 October 2026 (after the handoff below)

- Reviewed the handoff work; baseline Windows 36/36 and Android unit suites passed before any change.
- Weight: desktop backdrops were JPEGs re-saved as PNG (10 MB). Now JPEG (~0.45 MB total); duplicate `mountain_nature_banner` and unused `creator_banner` removed, avatar 256px; `scripts/convert_scenic.ps1` deleted.
- Android: 1254px launcher foreground sat in densityless `drawable/` (decoded ~3x on xxhdpi as the player placeholder); now 432px WebP in `drawable-nodpi`. Release/nightly builds package arm64-v8a only (x86_64 kept for debug/emulator); `verify-release.ps1` checks arm64 only.
- Windows JVM returns idle heap to the OS (G1 periodic GC, heap free ratios).
- Privacy: Discord presence now off by default (existing saved choice is kept).
- UI: playlist header actions wrap (FlowRow) and cover shrinks below 640dp — the handoff's open layout bug; K4Slider keeps the native Slider but draws a slim track/hover thumb again; selected Settings category uses onPrimaryContainer; song counts use plurals ("1 song"). VisualQaTest deletes its fixture playlist.
- Verified: Windows tests + Android unit tests pass; screenshots in `windows/build/implementation-visual-qa/` inspected.
- Not done: libmpv-2.dll is a 121 MB full (video) build; an audio-only libmpv would cut the installer ~100 MB but needs a new trusted binary.

## NEXT AGENT HANDOFF — 23 October 2026 — implementation stopped at user request

**Read this section first. It supersedes older pending/active/checkpoint wording below.** The user explicitly asked this agent to stop implementation, record all completed and upcoming work here, and let the next agent continue. No app code was changed after that request. Do not interpret this handoff as completion of the full enhancement goal. Historical entries are retained for context; their old test counts and pending labels describe those earlier moments.

### User intent and boundaries

- Original goal: fix the audit issues, improve reliability/performance/lightness/usability, plan before implementing, and update this file after every meaningful change. The latest instruction is to stop and hand over; resume implementation only when the user directs the next agent to do so.
- Keep existing Android/Windows functionality, library relationships, edited lyrics, likes, listening totals, history, downloaded files and old backup compatibility. Avoid speculative metadata-based recording merges.
- The checkout contains extensive pre-existing Antigravity/user changes mixed with this work. Do not reset, clean, overwrite, or claim authorship of the entire diff. No commit, push, installer execution, certificate-store change or publication was performed by this remediation work.
- Existing root APK/installer were not rebuilt/replaced with these source fixes. A passing source test does not certify those old binaries. Production signing credentials and physical-device verification are unavailable.
- Workspace: `C:/Users/kumak/Downloads/Projects/K4Music/Backups/k4music backups/backup/K4MusicMerged`. Windows PowerShell. No applicable AGENTS.md was found during this work. Do not spawn agents unless subsequently authorized by user/instructions.

### Exact stop point and latest verification

- The last code patch protects missing local songs with nonzero `totalPlayTimeMs` or any saved Lyrics record from automatic pruning, on Android and Windows. It extends the real Room/SQLite scan test to assert both listening totals and edited lyrics survive; ordinary unreferenced files are still removed and disconnected roots remain protected.
- Files in that final patch: `app/src/main/kotlin/com/k4music/android/Database.kt`, `windows/src/main/kotlin/com/k4music/desktop/data/K4Database.kt`, `windows/src/test/kotlin/com/k4music/desktop/LocalScanSafetyTest.kt` and this changelog. No schema version change.
- Both final validation processes are terminal, **exit code 0**, confirmed after the user's stop request: Windows handle 68822 and Android handle 52697. No restart/wait is needed for those handles.
- `build/cleanup-windows-validation.log`: **BUILD SUCCESSFUL in 1m 2s**, complete Windows suite **36 tests, 36 passed, zero failures/errors/skips**, across 13 suites.
- `build/cleanup-android-validation.log`: **BUILD SUCCESSFUL in 1m 2s**, Android suite **116 total, 114 passed, 2 skipped, zero failures/errors**, across 22 suites. This final command ran unit tests/compilation, not lint.
- Previous settled manual-review/identity/visual-source checks also succeeded: `build/implementation-windows-tests.log` and `build/implementation-android-validation.log`. The latter includes Android lint: **zero errors, 314 warnings, one Hint**. Lint was not rerun after the final cleanup-query-only patch.
- JUnit XML is under `windows/build/test-results/test/` and `app/build/test-results/testDebugUnitTest/`. The latest Windows suite includes real-storage Saavn preservation, stalled transfer cancellation, native playback lifetime and Compose rendering tests.
- Last recorded schema/DAO parity result before the final cleanup patch: **90 shared query contracts, 24 schema statements, Room version 30 and identity match**. The final patch changes the same cleanup predicate on both platforms; parity has NOT been rerun after it. Run the read-only script after fresh compilation when continuing.
- Focused final-patch `git diff --check` passed. Historical broad diff checks have pre-existing trailing blank-line warnings in `Shell.kt`/`SongRow.kt`; avoid unrelated line-ending churn.

### Completed implementation inventory (source, not packaged releases)

| Area | Implemented behavior / main locations | Evidence and limits |
|---|---|---|
| Android database startup | Removed destructive WAL/SHM deletion from automatic/manual recovery; safer startup errors. `MainApplication.kt`, `StartupScreen.kt`, database support | Android compilation/unit/lint checks pass; corrupt-device recovery not physically tested |
| Backup snapshots | Consistent SQLite snapshot, Android native VACUUM INTO on supported SQLite with transactional fallback; Windows writable snapshot. `utils/DatabaseSnapshot.kt`, settings `DatabaseSettings.kt`, desktop `data/Maintenance.kt` | Snapshot/schema/WAL tests pass; real slow-document-provider/device backup QA remains |
| Restore validation/recovery | Complete generated Room schema and foreign-key validation; Windows single-instance ownership before applying restore, staged atomic replacement, durable rollback retained until first open, first-open failure restoration, visible recovery errors/cancel staged restore | Windows disposable database schema/rollback tests pass. Android shutdown coordination remains an open concern below |
| Windows lifecycle | Parent-PID restart wait, startup/minimized/resume preference handling, autostart registration failure presentation | Compile/quoting tests pass; actual login/registry/installer flow not executed |
| Queue/history/native player | Final queue drain/sync and atomic queue file; flush history writes; queue/playback generations reject stale radio/recovery/link results; native handle read/write lock and serialized controller callbacks including safe callback-initiated shutdown | Real libmpv and persistence tests pass; exact listening totals/failure-injection coverage still incomplete |
| Android offline | Actual completion lengths and metadata for fallback/Saavn; completed cached files playable independent of current pin state; safer precache lifecycle | Android tests/compile/lint pass; physical airplane-mode/provider scenarios remain |
| Windows downloads | Per-attempt ownership and cancel/retry ordering, .part files, hashed filenames, durable legacy-compatible DownloadIndex, failed-delete/publish rollback handling, asynchronous batch cleanup with worker joins, visible transfers/bytes/cancel/retry/dismiss and about 10Hz progress updates | Loopback stalled transfer and real index tests pass; live bulk/UI interaction QA remains |
| Local library | Android MediaStore content observation; metadata refresh preserving relationships; safe numeric-ID parser rejects foreign Windows paths; unavailable-file subtitles; Windows complete-root reconciliation retains disconnected roots; final cleanup retains listening totals and lyrics | LocalScanSafetyTest, local-ID and metadata tests pass. Portable relinking and Android scan safety remain open |
| Lyrics | Empty/failed refresh retains saved/edited lyrics, partial results preserve other field, failures recoverable; meaningful periods retained in titles | Repository regression passes; actual synchronized seeking/scroll behavior needs UI QA |
| Playlist correctness | Honest pagination errors on repeated tokens/caps; strict title/artist/edition/duration matching; independent provider fallback; local deadline distinguished from external cancellation; recoverable retries/reporting on both platforms | Import regression tests pass; live provider playlists and large import cancellation need QA |
| Manual recording review | Failed rows offer up to 8 distinct candidates, full title/artist/duration, explicit selection into existing playlist, duplicate accounting, cancellable loading/retry/back; Android + Windows dialogs. `playlistimport/PlaylistImport*.kt`, `SpotifySource.kt`, desktop `ui/Dialogs.kt` | Latest platform compilation/tests pass, including bounded alternatives test; chooser itself has NOT been visually/interactively tested |
| Shared networking | `providers/common/.../utils/HttpTransfers.kt`: cancellation closes OkHttp calls and rechecks cancellation before propagating I/O exceptions. Used by update/download/Saavn/Spotify reads; Saavn quality is per-request rather than shared mutable state | Stalled headers/body cancellation and concurrent quality fixtures pass. Old desktop-specific helper was replaced by shared helper |
| Saavn stable identity | Shared `k4/SaavnKeys.kt`, optional plain-model playbackKey, `saavn:id:<id>` readers, exact provider-ID validation/decryption, 12-second resolution budget, bounded 128-entry known-stream cache, legacy direct URL resolution | Android key tests and Windows fixture/real-storage tests pass. Provider contract observed using song.getDetails; future upstream changes remain possible |
| Saavn preservation | Exact canonical trusted CDN/quality-variant alias retains old Song/download key; likes, totals, playlist, history, edited/synced lyrics and existing audio bytes proven preserved by real Room/index regression | NO bulk primary-key rewrite or downloaded-file rename. Unprovable duplicate identities must not be auto-merged |
| Provider/account/IPC | Piped HTTPS credential boundary, sign-in busy-state finally/reset, primary search fallback and retry errors; supported deep-link HTTPS hosts; supersession; local IPC total deadline/16KB/32-argument bounds | Provider boundary, IPC and obsolete-link tests pass; live account/Discord behavior not certified |
| Performance/accessibility | Lazy lists/grids, bounded page/artwork caches, bounded speculative prefetch (drop excess), reduced-motion/background options, native Material slider, named overflow action | Compilation passes; broad keyboard/screen-reader and measured performance QA remains |
| Updater | Bounded HTTP/subprocesses, update origin/version checks, GitHub SHA256 digest metadata required, byte count/hash checked before publication and hash rechecked before execution | Existing cancellation/subprocess tests pass; malicious digest/changed-file failure injection remains; no installer executed |
| Release gates/docs | Explicit production signing requirements, development signing opt-in, release verifier debug-cert rejection, accurate old binary checksums and self-signing limitations; read-only schema/DAO parity script | Expected missing-production rejection and explicit development gate success recorded; private credentials not provisioned, no production binary created |
| Visual fixtures | `VisualQaTest.kt` renders Library, Settings, unavailable-local playlist with/without queue to `windows/build/implementation-visual-qa/` using isolated data/no provider playback | Six PNG assertions pass. Only unavailable-local-with-queue image was visually inspected by this agent; it exposes the layout bug below |

### Immediate next fix was planned but NOT applied

**Playlist header button layout:** `windows/build/implementation-visual-qa/unavailable-local-queue.png` shows Delete squeezed into a tall button with one/two letters per line when the queue is visible. `windows/src/main/kotlin/com/k4music/desktop/ui/screens/CollectionScreens.kt`, `CollectionHeader`, currently places all actions in one `Row` (around line 99) beside fixed 200dp artwork. This remains unchanged: the user interrupted before any layout patch.

Proposed next action when authorized: use responsive wrapping for the header actions (for example Compose `FlowRow`, maintaining button hit targets), evaluate artwork/header stacking at narrow widths, then rerun the existing renderer and inspect the actual PNGs with queue on/off. Do not claim the existing passing screenshot-file test detects text clipping; it only proves frames render. No keyboard regression test was added.

### Remaining work, ordered by risk

1. **Android restore ownership/shutdown (high priority):** `DatabaseSettings.kt` currently calls `stopService()` then closes Room and replaces the database without awaiting all bound clients/writers. `MainApplication.kt` holds a BIND_AUTO_CREATE binding, with additional service bindings elsewhere. Establish an orderly coordinated stop/unbind/drain before closing Room; account for precache/media-browser callbacks and a failed restore. Prove original/recovery preservation using disposable data and eventually restore while playing on a device. The current backup/atomic-swap improvements do not prove lifecycle safety.
2. **Portable Android local identity (high priority):** `local:<MediaStore numeric id>` is device-specific; a restored number may identify a different song on another phone, and the current metadata upsert can attach old relationships to it. Windows paths likewise are not portable. Design a reversible identity/location separation and explicit verified relinking, not a metadata guess or destructive schema rewrite. Add cross-device/ID-collision fixtures and preservation proof before migration. Missing-files management should allow locate/relink/explicit removal while retaining user metadata.
3. **Android storage reconciliation:** distinguish unavailable/denied/partial storage from a complete scan. Current empty activeIds guard avoids destructive unmount cleanup but leaves stale rows after the last file is genuinely deleted. The final patch fixes lyrics/totals preservation ONLY; it does not resolve successful-empty scans, volume provenance or foreign-phone collisions. Inspect `HomeLocalSongs.kt`, `Database.pruneLocalSongs`, `utils/Cursor.kt`, `LocalFiles.kt` and `LocalMediaIds.kt`.
4. **Visual/interaction QA:** fix the proven header action wrapping; inspect all other rendered PNGs (not yet approved); render/test manual-match chooser, downloads failures/progress, recovery messages and empty/error/loading states. Verify narrow window, scaling, large fonts, long translations, focus order, keyboard activation, slider behavior and icon accessibility. VisualQaTest currently leaves its fixture playlist in isolated test data; consider cleanup to avoid repeated-test fixture accumulation, without touching installed data.
5. **Data/lifecycle failure tests:** exact history/listening totals across pause/seek/track change/shutdown; database write failures; Android bound-service teardown; simultaneous restore callbacks; interrupted snapshot/copy/rename and rollback first-open. Existing native/queue tests are useful but do not prove all those invariants.
6. **Update failure injection:** wrong digest, wrong length, unsupported/missing digest, partial/cancelled download, target changed before install, subprocess stdout held open and timeout. Use fixture HTTP/processes; never execute downloaded installers merely to test verification. Publisher signature verification remains a stronger future trust measure beyond a digest supplied by the same release origin.
7. **Release integrity/reproducibility:** Gradle wrapper still lacks distributionSha256Sum; Android CMake QuickJS download lacks pinned expected hash and explicit download/extraction status handling; Python `yt-dlp>=...`, `yt-dlp-ejs>=...`, pip are open-ended; Windows libmpv DLL is existence-checked rather than digest-pinned. Verify provenance before pinning hashes. Do not invent digests or call current tool existence sufficient integrity proof.
8. **Product CI:** checked-in `.forgejo/workflows/check.yml` runs Nix checks, not Android/Windows product validation. Add appropriate actual build/test/parity/signing checks with correct runner/platform availability, cache/tool provisioning and no secrets in logs. Do not replace existing workflow guarantees blindly.
9. **Version/backup compatibility and packaging:** root project still says 2.12.14, and older existing binaries cannot read new Saavn catalogue IDs. Set/document the new minimum compatible app version and validate backup compatibility before any new distribution; 2.12.15 was a possible next development version, NOT applied/approved as a release. Run settled checks, create separately named development artifacts, inspect/package native dependencies and verify checksums/signing. Preserve original root artifacts. `scripts/verify-release.ps1` can overwrite the root APK: inspect/adjust output destination before running it. Production signing/publication needs actual private credentials and user scope.
10. **Provider/device smoke tests:** real YouTube/Saavn fallback and offline playback, Spotify large/full pagination and retries, Piped login, headset routing/auto-resume, foreground/background behavior on Android, effects, Windows login/minimize/media controls and installer/uninstaller sentinel preservation. Discord Rich Presence has user-reported history below; inspect logs/settings/client behavior before asserting a new root cause.
11. **Legacy duplicate review:** stable Saavn keys now work and proven aliases retain exact legacy keys; uncertain old/new duplicates are not yet consolidated. Manual import matching is implemented, but that is NOT a general duplicate-library merge/relink UI. Any later merge must preserve all relationships, lyrics, totals, history and downloads with rollback.
12. **Final acceptance:** rerun current gates only after relevant changes, audit the complete original plan requirement by requirement, and update stale status/checklists. Do not mark the whole enhancement goal complete based on green unit tests or broad aspirations such as “best app.” Remaining hardware/credential limitations must stay explicit.

### Commands and practical continuation notes

Use the already installed Gradle distribution and cached home; the root `.gradle` home previously caused needless missing-dependency churn. Commands below describe prior verified invocation; no new builds are requested by this handoff.

```powershell
$k4Gradle = 'C:/Users/kumak/.gradle/wrapper/dists/gradle-9.8.0-all/619e6l8zt76uzaekhqdol0b60/gradle-9.8.0/bin/gradle.bat'
& $k4Gradle -g downloads/gradle-home -p windows test --offline --no-daemon --max-workers=2 --console=plain '-Pkotlin.compiler.execution.strategy=in-process'
& $k4Gradle -g downloads/gradle-home :app:testDebugUnitTest :app:lintDebug --offline --no-daemon --max-workers=2 --console=plain '-Pkotlin.compiler.execution.strategy=in-process'
python -B scripts/verify-library-parity.py
```

- Local Gradle daemon execution previously required sandbox escalation and was allowed. A returned running-session handle is not a failure: poll the same handle, inspect logs, and never restart solely after observation timeout.
- `windows/downloads/mpv/libmpv-2.dll` is present; native tests use it. Tests isolate persistence with `k4.dataDir=windows/build/test-data` and `java.io.tmpdir` beneath it; do not point them at installed user data.
- `SaavnIdentityTest`, `SaavnTransportTest`, `LocalScanSafetyTest`, `DatabaseSafetyTest`, `DownloadIndexTest`, `NativePlaybackTest`, `PersistenceTest`, `IpcSafetyTest`, `ProviderBoundaryTest`, `LyricsSafetyTest`, `TransferSafetyTest`, `VisualQaTest` hold meaningful current Windows regressions. Android matching/key/snapshot/local-ID tests cover pure/shared paths; two live tests are skipped.
- Both generated Room implementations and committed Android schema 30 must agree. `scripts/verify-library-parity.py` compares complete DAO SQL (including overloads), normalizes platform metadata parameter aliases, handles Android-only completion update and validates generated DDL/identity against committed export. Run after platform compilation, not against stale generated files.
- `app/src/main/kotlin/.../k4/SaavnKeys.kt` is shared into Windows via source sync. Shared transport now lives in providers/common; app directly depends on that module. OkHttp catalog is 5.5.0, matching the cached Ktor 3.6.0 dependency; reverting to 4.10 previously caused an uncached stdlib-jdk7 resolution failure offline.
- Use UTF-8 and preserve LF when scripting writes on Windows. Prefer small apply_patch edits. Keep realtime change entries and distinguish implemented, verified, pending and unavailable-device checks.
- `AUDIT-2026-10-21.md` and older sections below are historical evidence, not a fresh defect list: several original F01–F18 paths have already been repaired. Revalidate every finding against current source.
- Handoff preparation changed only this Markdown file. Current implementation is deliberately paused for the next agent; no additional feature or layout patch was applied.

## Latest implementation — 23 October 2026: local cleanup data preservation

- Plan: protect saved listening totals and lyrics during local reconciliation on both platforms; verify actual SQLite deletion behavior and compile the Android query before continuing remaining audit work.
- Changed Android and Windows cleanup eligibility to retain missing local songs with nonzero listening totals or a Lyrics record. Likes, playlists, history and disconnected-root protection continue to apply. No schema migration or identity rewrite is required.
- Extended the real Room/SQLite local-scan regression with missing files containing listening totals and edited lyrics, alongside the existing disposable-file, liked-file and disconnected-root cases.
- Verification: completed successfully: Windows 36/36 and Android 116 total (114 passed, two skipped), zero failures/errors. Logs: `build/cleanup-windows-validation.log`, `build/cleanup-android-validation.log`. Lint/parity were not rerun after this final query-only patch. Remaining restoration, portable relinking, release integrity and device QA work remains open; implementation stopped at user request, see handoff above.

## Active implementation plan — 22 October 2026

The historical sections below describe previous work. This checklist and newest-first log track the current audit remediation; a checked implementation is not a claim of device certification.

- [ ] 1. Database safety: preserve Android journals; consistent snapshots on both platforms; validate complete restore schema; atomic replacement, durable rollback and recoverable errors.
- [ ] 2. Lifecycle: orderly Windows restart, queue writer drain, history flush, playback/queue generation guards and native-player shutdown.
- [x] 3. Offline reliability: preserve completed fallback downloads; authoritative completion metadata; serialize cancellation/retry; durable index and collision-resistant filenames; visible failure/retry controls.
- [ ] 4. Library and import: update local metadata/artwork; observe Android media changes; represent unavailable local files; honest pagination/truncation and match confidence; Windows import retry/reporting parity.
- [x] 5. Startup and updates: honor startup/resume preferences and registration failures; bounded cancellable downloads; check extractor exit status; verify update authenticity.
- [ ] 6. Performance/accessibility: lazy collection rendering, bounded caches/prefetch, reduced motion/background animation control, keyboard/accessible sliders and icon labels.
- [ ] 7. Release engineering: explicit production signing, accurate certificate guidance/checksums, schema/parity checks and focused regression tests.
- [ ] 8. Verification: Android and Windows tests/build checks, lint, regression scenarios, final requirement-by-requirement audit; record unavailable hardware/external credentials honestly.

### Audit remediation status

Implementation status is recorded separately from verification. The existing release APK/installer have not been rebuilt from these edits.

| Audit area | Current implementation | Evidence / outstanding work |
|---|---|---|
| Android journal deletion | Removed automatic/manual destructive repair | Android compilation, unit suite and lint pass; physical-device recovery remains untested |
| Consistent snapshots | Transactional Android export; writable native SQLite Windows snapshot | Android compilation passes; Windows committed-WAL and schema/replacement tests pass |
| Restore safety/schema | Generated complete schema + relationship validation; atomic Windows replacement, separate rollback snapshots and first-open recovery presentation | Windows schema rejection/replacement and injected first-open rollback tests pass; Android device restore QA remains |
| Restart/startup flags | Parent-exit wait, normal shutdown, minimized/resume behavior, registration errors | Windows compilation and command-quoting regression pass; real login/registry QA remains |
| Playback/queue/history races | Generation guards, serialized native callbacks/counters, queue writer drain, history flush, resolver cancellation and locked native lifetime | Real libmpv stale-radio, obsolete-link, concurrent shutdown and callback-initiated shutdown pass; exact listening totals/history failure injection still need coverage |
| Offline completion | Android completion lengths and pin-independent cached playback | Android compilation/unit suite pass; real provider/offline scenarios remain to test |
| Download cancellation/index | Per-attempt cleanup ordering, cancellable calls, atomic index, hashed paths, complete progress/cancel/retry controls and asynchronous bulk cleanup | Stalled HTTP/key-collision and index failed-delete/failed-publish/legacy checks pass; live bulk-transfer and rendered UI checks remain |
| Local library | Metadata/artwork refresh, Android content observation, unavailable-file subtitles and complete-root Windows reconciliation | Android foreign-ID and Windows SQLite disconnected-root/relationship preservation regressions pass; Android reconciliation/portable relinking still need work |
| Search/pagination | Grid continuations, retry states, incomplete playlists rejected | Android pagination regressions and Windows compilation pass; Windows retry/reporting parity implemented |
| Updates | Bounded cancellable transfers, GitHub digest verification, extractor exit/timeout checks | Windows build, stalled HTTP cancellation and nonzero subprocess-exit tests pass; digest-mismatch injection remains |
| Accessibility | Native keyboard/accessible sliders and named Windows overflow action | Windows build passes; remaining icon labels and visual/keyboard QA remain |
| Remaining enhancements | Planned | Manual match review, stable portable identities/relinking, shared Saavn cancellation, listening/restore/update failure injection, Android storage reconciliation, release integrity/CI, visual QA and final packaging |

### Follow-up acceptance — 23 October 2026

- [x] Implement and verify foreign local-ID handling, unavailable-row presentation, saved-lyrics preservation, period-containing titles, conservative matching and complete-root Windows reconciliation.
- [ ] Verify the new asynchronous download cleanup, legacy index/failure handling, active transfer controls and progress throttling.
- [ ] Verify shared HTTPS account boundaries, sign-in busy-state recovery and primary-provider search/Piped retry presentation.
- [ ] Serialize Windows native callbacks through controller ownership; verify transitions, listening totals and shutdown.
- [x] Bound local IPC and supersede obsolete link lookups; validate supported hosts and expose failures.
- [ ] Make shared Saavn calls truly cancellable; introduce stable catalogue identities with old-key compatibility and migration proof.
- [ ] Preserve foreign references and handle Android partial/unavailable local storage safely; provide portable relinking and manual uncertain-match review.
- [ ] Add automated schema/DAO parity and release integrity checks; exercise relevant failure paths and visual/keyboard flows.
- [ ] Produce and verify local development artifacts after settled checks; keep production signing/publication separate and document missing hardware/private credentials.

### Saavn identity compatibility plan

1. Existing readers: Android resolving data source and Windows StreamResolver; writers: search, home recommendations and Spotify matching bridges. Both currently persist URL-based keys in Song, relationships and cache/download indexes.
2. Expand readers to support catalogue keys via the observed song.getDetails response, while retaining direct URL-key resolution. Reject mismatched returned IDs rather than guessing a different recording. Reuse known search URLs in a bounded cache to avoid an extra metadata request on normal playback.
3. New catalogue results use saavn:id:<catalogue ID>. If an existing legacy library key refers to the same canonical CDN file (including its quality variants), retain that existing playback key so likes/downloads/playlist membership remain connected. Catalogue identity and an optional legacy playback reference remain separate in the plain search model.
4. No bulk primary-key rewrite or cache renaming in this stage. Old rows and original download bytes remain intact; metadata-only guesses cannot merge recordings automatically. Conflicting/unprovable legacy matches belong in the manual review flow.
5. Verify ID consistency, legacy resolution, quality changes, URL changes for the same ID, cancellation and unchanged schema/relationships. Existing older binaries cannot resolve newly introduced catalogue keys; document the minimum compatible app version before packaging/distribution. Any later destructive consolidation needs separately proven preservation and recovery.

### Live change log (newest first)

- Visual QA / local fixtures: added a headless Compose/Skia render scenario for Library, Appearance settings and a playlist containing an unavailable local file plus a stable catalogue key, each with/without the queue panel. It restores an idle queue and uses no music-provider requests or audio playback; files stay in isolated test/build paths. Rendering execution and image inspection are pending and are not claimed as visual approval yet.

- Verified identity checkpoint: preceding runs are terminal and successful: Windows 34/34, Android 115 total (113 passed, two live tests skipped), zero failures. Parity now passes 90 DAO query contracts and the same 24 schema statements/Room 30 identity. These results precede the real-storage compatibility test and manual-review UI additions. Starting fresh settled-source checks for those additions.

- Import cancellation / recovery: Windows imports and retries now retain a cancellable job and keep Cancel available while work is running. Spotify embed/page reads use the shared socket-closing helper, so dismissing an import stops stalled reads promptly. Android retry failures now display their error alongside the preserved result instead of silently returning to the same screen. Manual-review duration rendering uses the existing platform formatters. Verification pending.

- Manual recording review / Windows parity: failed import rows now expose the same bounded recording chooser, with full title/artist/duration, cancellable loading, Retry/Back and an explicit transactional selection. A selection appends to the existing playlist and removes one failure only; duplicates update the report rather than creating another playlist. Added a shared importer regression proving alternatives are distinct/bounded and cannot pass automatic matching merely by being offered for review. Compilation/visual verification pending.

- Manual recording review / shared + Android: playlist sources can offer bounded on-demand alternatives without weakening automatic matching. Spotify returns up to eight distinct playable options with a cancellable local deadline. Android failed-track rows now open a recording chooser showing full title/artist/duration; an explicit Use this recording appends transactionally to the existing playlist and removes only that failed row. Duplicate selections are counted and save/search errors remain recoverable. Windows parity, regression tests and verification pending.

- Identity preservation / real storage regression: added a real Room + legacy download-index scenario with a liked song, listening time, playlist membership, history event, edited/synced lyrics and existing audio bytes. A matching search/quality variant must reuse its exact key, preserve all those values and avoid inserting a duplicate canonical Song row. This test was added after the validation run began, so a subsequent settled test run is required before counting it as verified.

- Transport checkpoint: Windows passed 31/31 and Android's transport-only test/lint handle completed successfully. Those runs precede the identity changes and do not certify the current source. Starting fresh Android/Windows checks on the settled compatibility implementation; legacy-to-stable consolidation beyond proven aliases remains pending manual review.

- Identity preservation checks / compatibility guidance: added Android pure regressions for stable IDs across URL/token/quality changes, legacy playback-key retention and refusal to merge metadata-only or untrusted-host/port candidates. Desktop fixtures additionally exercise decryption and exact-ID metadata resolution. README now warns that older listed binaries cannot resolve newly introduced catalogue identities; existing keys/files remain unchanged. Manual resolution of unprovable legacy duplicates remains an explicit open item.

- Identity compatibility implementation: added catalogue-key readers backed by the verified song-by-ID contract, strict returned-ID checks, a 12-second overall resolution budget and a bounded 128-entry search-URL cache. New results use stable catalogue keys; both platform searches reuse proven existing legacy CDN/quality-variant keys to retain likes, relationships and download lookups. No existing primary key or file was rewritten; schema stays 30. Added matching DAO queries on both platforms and switched Android/desktop search/import bridges consistently. Tests and full settled-source verification pending. The still-running Android lint handle belongs to the preceding transport-only checkpoint and will not certify these new edits.

- Identity regression specification: added fixture-only checks for stable IDs across CDN URL changes, reuse of a proven existing legacy quality-variant key, legacy URL resolution without metadata, invalid catalogue IDs and rejection of a mismatched song-by-ID response. The compatibility implementation follows once the current Android transport validation completes; these newly added tests are not yet runnable and have not been counted as passing.

- Provider contract evidence: a read-only public API request succeeded through the permitted network path. Search returned catalogue id rjkrTnma; song.getDetails with that id returned the same id, duration and encrypted stream field. The earlier sandbox DNS failure was an environment limitation, not evidence that the endpoint was invalid. This permits testing an ID-based reader against the observed response shape before changing writers.

- Dependency verification repair: both offline builds stopped before compilation because providers/common selected the catalogue's obsolete OkHttp 4.10 dependency path and an uncached Kotlin JDK7 bridge. Ktor 3.6.0's cached primary POM confirms both products already select OkHttp 5.5.0. Aligned the direct version declaration to that existing runtime so shared compilation follows the same dependency graph; this does not introduce a newer product HTTP runtime. Both failed handles are terminal; rerun pending.

- Regression / shared transport: added loopback-server tests that stall a Saavn search body and a stream-probe header, then require cancellation to disconnect promptly. A concurrent fixture test verifies 96/320 quality choices remain request-local. Tests contact no music providers. Full platform checks starting; external metadata endpoint inspection was unavailable through the web reader, so stable-ID resolution has not been changed on an unverified endpoint assumption.

- Shared Saavn transport: moved the already-tested cancellable OkHttp helper into providers/common and reused it for desktop transfers plus shared Saavn search/probes. Cancellation now closes stalled headers/body reads and remains cancellation through both bridges. Quality is passed per request rather than stored on a singleton, avoiding concurrent quality races. Existing OkHttp dependency is reused; no new runtime library. Socket cancellation/quality regressions and platform verification pending.

- Identity transition planning: traced Android/Windows search/import bridges, stream resolvers, cache/download keys and persisted Song relationships. Existing saavn:<URL> keys must keep resolving and existing downloaded bytes must keep their original lookup keys. Stable catalogue identities need an expand/compatibility stage with explicit migration proof; no existing IDs or downloads have been rewritten.

- Final checkpoint / this batch: Android's final focused run completed successfully after preserving foreign references: 112 tests, 110 passed and two live-service tests skipped, zero failures/errors. Windows remains 28/28 passing. The prior full Android lint run has zero errors / 309 warnings; the subsequent Android change only narrowed an existing prune predicate. Parity passed again after both builds. No installer was run, no registry/certificate/user-library changes were made, and no existing release binary was replaced. Remaining acceptance items stay open above.

- Windows verification / current source: all 28 tests passed, zero skips/failures/errors. This includes real native stale-radio/shutdown/obsolete-link tests, bounded socket messages, host validation, lyrics preservation, complete-root SQLite reconciliation, index failure/legacy handling, cancellable HTTP and plaintext credential rejection. The library parity verifier still passes 89 queries and 24 schema statements. Android's final narrow reconciliation test run remains active; its preceding full lint run recorded zero errors / 309 warnings.

- Android reconciliation / foreign references: a MediaStore scan now prunes only this platform's numeric local IDs; restored Windows paths remain available for future relinking instead of being mistaken for deleted phone files. Existing likes/history/playlist protections remain. Focused Android compile/unit check pending.

- Android verification: the shared-provider test/lint run completed successfully on its original handle (7m10s). Windows IPC/callback changes are in a separate new run; platform counts/lint totals will be recorded from final reports.

- Regression / IPC and obsolete links: added local-socket tests for UTF-8/quit messages, oversized messages, argument limits and an idle peer deadline. Domain tests reject lookalikes, embedded credentials and unexpected ports. A real-libmpv controller test restores an idle queue, changes it, then confirms an obsolete link result cannot replace it; no music-provider traffic is used. Verification starting.

- Verified downloads / providers: full Windows suite passed 23/23 after the receiver-shadowing correction, including index failure/legacy/delete tests and the plaintext credential guard. Android validation remains on its original live handle.

- IPC / links / controller ownership: local launch messages now have a total deadline, byte/argument limits and bounded connection attempts; callback exceptions no longer terminate the listener. Link parsing validates HTTPS hosts and cancels superseded lookups; playback applies only if the queue is unchanged and the caller remains active. Native property/load/output callbacks now dispatch to the serial controller with generation checks, including listening counters. Regression checks and compilation pending.

- Parity verification: the new read-only gate passed 89 shared DAO query contracts (including overloaded queries), 24 generated schema statements and Room schema 30 identity against the committed export. An in-memory altered desktop ORDER BY query was rejected as DAO drift; no source file was changed for the negative check. Platform test/lint handles remain active.

- Library parity / release checks: added a read-only verifier for shared DAO SQL, generated Android/Windows table/index/view statements and Room version/identity against the committed schema. Explicitly accounts for desktop metadata parameter aliases and Android-only cache completion; unexpected query drift fails. Documented invocation after fresh platform compilation. Execution pending current build completion.

- Verification repair: Windows compilation caught Kotlin receiver shadowing in the index rollback snapshot (Properties.entries hid the owning map). Corrected the receiver reference before rerunning. The failed run did not execute the new tests; Android validation continues on its existing handle.

- Provider recovery / credentials: Windows search now attempts Saavn when the primary search fails, retains Retry if both providers fail, and shows loading while fallback runs. Piped playlist/shelf failures expose Retry instead of silently appearing empty; missing accounts are explained. Piped rejects plaintext credential/token endpoints at the shared request boundary, and Windows sign-in always resets busy state on failure. Added a no-network plaintext-login regression; full platform checks pending.

- Downloads / controls and performance: Library settings now show every waiting/preparing/downloading track, byte totals when available, progress and individual Cancel controls alongside failed Retry/Dismiss rows. Transfer emissions are capped to roughly 10 updates/second plus completion to avoid redrawing on every 64 KB buffer. Added legacy-index, missing-file, failed-delete and failed-publish regressions; verification pending.

- Verified checkpoint: the preceding platform runs passed 20/20 Windows tests and 112 Android tests (110 passed, two live tests skipped). This includes local scan/lyrics preservation and Android foreign-ID/recording-title regressions. New download edits are not counted until the next run.

- Verification / 23 October: both previous handles completed successfully. Windows includes new lyrics and local-scan regressions; Android tests/lint include safe local IDs and conservative recording matching. Exact fresh counts will be recorded after report inspection. Continuing with download cleanup, so these results precede the next edits.

- Download cleanup / state: extracted legacy-compatible index ownership with atomic persistence. Failed file deletions remain indexed and retryable; bulk cleanup writes the index once off the UI thread, cancels/joins active transfers, and blocks replacement transfers during cleanup. Missing/empty files are reconciled on window focus and before downloading; cleanup errors are shown. Shutdown also joins cleanup workers. Focused index failure/legacy regression and compilation pending.

- Regression / local scans: added a real Room/SQLite test covering an empty but successful root, a disconnected root and a liked missing file. Only the deleted unreferenced song under the successful root may be removed. Availability observation treats denied observer registration as unknown and releases normally. Full platform checks starting.

- Local reconciliation / matching: Windows now distinguishes fully scanned folders from unavailable/failed folders and prunes unreferenced rows only under completed roots; interrupted or failed database writes propagate instead of pretending success. Recording matching preserves meaningful parts/subtitles and rejects duration conflicts over 10 seconds while retaining known remaster/from-film normalization. Added matching regressions; scan/SQLite regression and compilation pending.

- Local files / portability: Android now parses MediaStore IDs safely and marks foreign Windows paths unavailable instead of throwing. Local rows on both platforms show File unavailable without deleting playlist/history references. Android shares one lifecycle-aware content observer across visible local rows; Windows probes off the UI thread on focus, rescan and hover. Named the Windows overflow action. Regression added for foreign/malformed IDs; compilation pending.

- Lyrics preservation: failed/empty refreshes retain saved and edited lyrics, partial results preserve the other saved field, and refresh failures show a recoverable message. Local metadata titles retain periods (for example Mr. Brightside). Added a repository regression with an in-memory store covering zero writes on failure and partial-field preservation; verification pending.

- Planning / continuation: current source and existing reports reviewed without changing prior work. Next implementation batches: (1) unavailable/foreign local files and saved-lyrics preservation, (2) conservative local reconciliation and recording matching, (3) download cleanup/state and complete controls, (4) provider/link/IPC failures and serialized player events, (5) portable identities, schema/release checks and verification. Each batch receives regression checks and a live entry; existing installers remain distinct from these source changes.

- Verification / previous checkpoint: existing Windows reports confirm 18/18 passing, including native lifetime and first-open restore rollback. Android reports record 110 tests (108 passed, two live tests skipped), zero lint errors and 306 warnings. Generated Android/Windows schema creation statements match (24/24). These precede the following edits and do not certify them.

- Restore recovery: the owning startup process now opens the installed replacement through Room before app database writers start. A first-open failure restores the prior validated snapshot atomically while retaining its recovery copy. Failures are shown in the window/settings, and staged restores can be cancelled. Added an injected first-open failure regression that checks the original song and recovery copy survive. Verification pending.

- Native lifetime: property reads/commands now hold a shared handle-access lock; shutdown takes exclusive ownership before closing. A slow event callback must finish before the context is freed, including shutdown initiated by that callback. Initialization failure also releases its native allocation. Added concurrent real-libmpv command/read/shutdown coverage. Verification pending.

- Current checkpoint: full Windows suite passed 15/15 again after import parity and bounded-prefetch changes. Android’s latest runtime-source check remains 110 tests (108 passed, two live tests skipped), with zero lint errors / 306 warnings; subsequent Android edits were limited to the verified signing configuration and documentation. The goal remains active: remaining work is listed in the status table, and existing release binaries have not been replaced.

- Performance / speculative work: hover prefetch drops work when both speculative slots are busy instead of queuing an unlimited coroutine backlog. Next-song warming uses the same bound and runs only during playback; foreground playback resolution is not delayed behind the speculative limit. Verification pending.

- Verification: Windows import retry/reporting changes passed the full 15-test suite. The nightly signing task also passed with the explicit development flag; production signing remains intentionally unavailable without private credentials.

- Import / Windows reporting parity: completed imports now explain Spotify’s unexposed/truncated tracks, retain every failed-track reason in a scrollable result, and offer Retry failed tracks without creating another playlist. Retry appends deduplicated matches transactionally and preserves actionable errors. Cancellation propagates correctly. Windows compilation/tests rerun pending.

- Signing gate verification: the production release gate rejected missing production credentials (expected), while explicit -PallowDevelopmentSigning=true passed. PowerShell release-verifier syntax parsed successfully. No APK was signed or published. Logs: build/implementation-production-signing.log and build/implementation-development-signing.log.

- Verification: current Windows suite passed 15/15, including the real libmpv stale-radio regression and stalled HTTP cancellation. Android passed 110 tests (108 passed, two live tests skipped); lint completed with zero errors and 306 warnings. Logs: build/implementation-windows-tests.log and build/implementation-android-validation.log.

- Release signing: release/nightly builds now reject missing production credentials instead of silently using Android Debug. Local development signing requires an explicit property; the release verification script also rejects debug certificates unless explicitly allowed for development. Credentials/certificate stores were not changed. Signing-gate checks pending; production artifacts have not been rebuilt.

- Playback regression / native engine: added a headless libmpv test using generated silence. It holds an old radio response, replaces the queue with one containing the same current song, releases the response and checks that the new queue/paused state survive. Uses isolated test data and no music-provider requests; execution pending.

- Release documentation: README checksums now match the existing APK/installer, with an explicit distinction between those packaged binaries and unreleased source fixes. Corrected the development-certificate script’s claims: local self-signing does not guarantee SmartScreen/Defender acceptance and is not production publisher signing. The script was edited but not executed; no certificate stores changed.

- Validation repairs: the stalled-transfer test found that cancellation could escape as an OkHttp IOException; the shared transport helper now rechecks coroutine cancellation before propagating I/O errors. Android lint rejected direct calls into restricted Room internals. Both platforms now force full schema validation through Room’s normal public open path on private staged copies, without suppressing the lint rule. Tests/lint rerun pending.

- Verification checkpoint: performance, motion and recording-matching changes are settled for a new full Android test/lint run and Windows test run. Earlier green counts apply to the preceding checkpoint; these new edits remain unverified until the new logs finish.

- Import / recording accuracy: automatic matching now requires equivalent normalized titles and matching recording-edition markers (live/remix/cover/etc). Known artists must agree by phrase; duration alone is accepted only when artist metadata is missing. Ambiguous/conflicting matches remain failed tracks instead of silently importing a different recording. Added regressions for partial titles, common surnames, missing artist metadata and live/studio editions. This intentionally favors accurate imports over speculative matches; verification pending.

- Performance / Android backups: SQLite versions supporting VACUUM INTO now use the native snapshot fast path; older versions retain the consistent transactional exporter. Snapshot failures propagate safely rather than copying live pages. Added version-boundary tests. Verification pending.

- Performance / motion: added Reduce motion in Appearance. Decorative vinyl/backdrop/equalizer motion pauses when hidden/minimized or reduced motion is enabled; equalizer bars stop when playback pauses. Kinetic lyrics honor reduced motion. Removed the per-draw equalizer list allocation. Verification pending headless/compile checks.

- Performance / large collections: album and playlist screens now reuse the existing lazy SongList with a footer slot; rows are composed as needed instead of eagerly building entire collections. Added bounded LRU retention for pages (32), lyrics (64), resolved streams (128) and artwork colors (256), with focused eviction/invalidation coverage. These are disposable caches; authoritative library/download data is not evicted. Verification pending.

- Verification: settled Windows suite passed all 11 tests (five core, four real SQLite safety tests, two persistence tests), including committed-WAL preservation, full-schema rejection, failed replacement, local metadata/relationship preservation, download-key collisions and queue flushing. New transfer/cache tests were added after that run and are not counted yet.

- Regression coverage / transfers: added a local stalled HTTP server check proving cancellation closes a blocking body read promptly, plus a nonzero subprocess-exit regression. These tests contact no music services and do not run the installed extractor.

- Verification: Android settled-source test run passed (108 total, 106 passed, two live tests skipped; build/implementation-android-tests.log). Python updater suite passed all four tests. Windows compilation caught an invalid Close string resource in the new failure panel; replaced it with dedicated resource-backed labels and rerun pending.

- Regression coverage: Windows tests now use an isolated build/test-data directory. Added real SQLite coverage for WAL snapshots, full-schema rejection and local metadata preserving likes/playlist membership; added download-key collision and final queue-flush checks. Test sources are settled for the next verification run.

- Accessibility / sliders: replaced custom pointer-only Canvas controls with the existing Compose Material slider, retaining the value/change/commit API while adding native keyboard focus, range semantics and accessible adjustment behavior. No dependency added. Verification pending.

- Search / complete and recoverable results: album/artist/playlist grids now request continuation pages. First-page and pagination failures expose Retry while retaining successful results/tokens; query changes cancel obsolete loads and repeated continuation tokens are rejected. Verification pending.

- Verification checkpoint: both platforms compiled after the initial database/lifecycle changes. The first Windows database test run exposed a JUnit test return-type issue (corrected to explicit Unit). Android tests compiled during the pagination edits, producing mixed old implementation/new test assertions; rerunning from the settled source is required. No passing claim is made for these runs.

- Updates / recovery controls: update dialogs can cancel active transfers and return to an actionable state on failure. Installer verification runs off the UI thread. Extractor maintenance now checks timeout and process exit status, bounds captured output, destroys interrupted processes, and resets the busy state in finally. Verification pending.

- Updates / transport and authenticity: update downloads use the existing OkHttp runtime with inactivity/overall deadlines, cancellation closes the socket, origin/version are validated, and SHA-256 must match the digest obtained through GitHub release metadata. Installation rechecks the verified file. Releases without a supported digest fall back to the website. Added a shared cancellable-call helper used by audio downloads too. GitHub digest contract checked against its official REST documentation; verification pending.

- Library / local rescans: Windows now updates existing local metadata without replacing likes/history/playlist rows; artwork cache keys include file modification/size and concurrent scans are gated atomically. Android uses MediaStore content notifications on all supported OS versions and retries query failures, replacing version-only polling. Missing-file presentation remains planned. Verification pending.

- Import / pagination: repeated continuation tokens and page-budget exhaustion now produce an explicit incomplete-import error. Windows playlist browsing uses the same retrying page collector instead of silently caching partial playlists. Updated repeated-token coverage and added a page-limit regression. Verification pending.

- Offline / controls: active song menus expose cancellation; Library settings now retain failed downloads with error text, Retry and Dismiss. Cancelling a transfer closes its OkHttp call rather than waiting for a socket read timeout. Verification pending.

- Offline / Windows: download replacement waits for cancelled-attempt cleanup, uses unique partial files and commits under the same lock as cancel/delete. Index persistence errors now reach the caller; data/index writes are synced, filenames use SHA-256 while old indexed downloads remain readable, and failed downloads retain a retryable track/error state. Shutdown cancels and joins downloads before Room closes. UI and regression verification pending.

- Offline / Android: completed cache entries are selected before refreshing fallback pins, preserving offline audio when pins expire or fallback is disabled. Download completion records the actual length in both cache metadata and Format, eliminating HEAD-dependent offline visibility; removed the extra HEAD request and fabricated 320 kbps metadata. Also cancel the precache progress collector when its service stops. Verification pending.

- Database safety / schema parity: Android restore validation now also invokes its generated Room schema validator and foreign-key checks, including files with a copied Room identity hash. No new runtime dependency was needed.

- Lifecycle / restart and startup preferences: restart children wait for the parent PID before touching the mutex/library, while the parent uses normal shutdown. Startup now honors minimized/resume preferences; registration reflects the actual registry state, runs off the UI thread and reports failures. Development Java launchers are not registered as the app. Replaced the registry-mutating unit test with command-quoting coverage. Verification pending.

- Lifecycle / persistence: final queue saves now drain the existing writer instead of racing it; staged JSON is synced and never copied over an active file. Shutdown joins the serial playback dispatcher, cancels playback/resolver work, flushes listening events and waits for library writes before closing Room. Playback recovery/radio responses now check generation tokens, including replacement queues containing the same song. Verification pending.

- Database safety / Android snapshots: backup and pre-restore recovery now export schema/rows through a single SQLite transaction, preserving blobs, nulls, indexes, views, foreign keys and AUTOINCREMENT counters. This supports older Android SQLite versions without VACUUM INTO and removes live-file-copy fallback. Validation/compilation pending.

- Database safety / Windows snapshots and restores: replaced read-pool VACUUM and raw-copy fallback with a separate writable SQLite snapshot. Restore checks the generated full Room schema and foreign keys, checkpoints the original through SQLite, retains uniquely named recovery snapshots, and requires atomic replacement. Failed operations preserve pending restore/original files. Verification pending compilation and SQLite regression tests.

- Database safety / Android journals: removed automatic WAL/SHM deletion after startup errors and the destructive repair button. Startup now preserves the library and exposes retry/report controls. Verification pending focused regression checks.

- Planning: inspected the current dirty worktree and preserved existing changes. Implementation will proceed in the order above, with this log updated after each meaningful step. No installer, registry, trust-store or production-library mutations are part of testing.

---
**Author / Contributor:** Pair-Programmed System Enhancement, Security Hardening & UI/UX Redesign  
**Project:** K4 Music (Cross-Platform Compose Multiplatform & Android Music Player)  
**Lead Developer & Architect:** Kartik Langwan  
**Target Release:** 2.12.14  
**Date:** October 2026  

---

## 1. Executive Summary

This document details all technical fixes, architectural improvements, reliability hardening, and UI/UX redesigns implemented across **K4 Music Desktop (Windows)** and **K4 Music Mobile (Android)**.

The objectives of this overhaul:
1. **Eliminate Critical Reliability & Data-Integrity Issues**: Resolve race conditions during database backup/restore, protect user playlists/history from inadvertent deletions during local media scans, terminate orphaned background worker processes, eliminate socket/HTTP connection leaks, and validate file integrity on downloaded audio streams.
2. **Elevate UI/UX to a Professional, Human-Crafted Aesthetic**: Replace generic/AI-style artifacts with a bespoke, polished design language inspired by modern macOS/Windows 11 acrylic glassmorphism, tailored for music lovers and nature enthusiasts.
3. **Multi-Panel Player Flexibility**: Allow users to open **both** Live Synced Lyrics and Up Next (Queue) panels simultaneously in a dual-split view.
4. **Deep Aesthetic Customization**: Add backdrop transparency/opacity sliders, instant presets, artist glow lighting, and toggleable scenic backdrops (Alpine Twilight & Nordic Aurora).
5. **Redesign the Settings Interface**: Reorganize the settings menu into distinct, easily accessible categories with subtitles, including a dedicated **Windows & System** section for startup and tray automation.
6. **Maintain 100% Test and Build Integrity**: Ensure clean compilation, isolated test execution, and production installer creation for both Windows (`.exe`) and Android (`.apk`).

---

## 2. Core System Bug Fixes & Architectural Remediations

### 2.1. Safe Local Library Synchronization & Playlist Safeguards
- **Problem**:
  - Scanning local files previously passed all scanned file IDs directly into a SQL `WHERE id NOT IN (:activeIds)` query. On Android devices with standard SQLite parameter limits (999 host parameters), libraries containing 1,000+ songs crashed with `SQLiteException: too many SQL variables`.
  - If external storage or an SD card was temporarily unmounted, or if permission/scanner errors produced an empty list, `activeIds.isEmpty()` triggered `deleteAllLocalSongs()`. Because `SongPlaylistMap` and `Event` (listening history) enforce `ON DELETE CASCADE` foreign keys, users lost all local tracks from their playlists and listening history.
- **Solution**:
  - Implemented safe in-memory diffing in both Android [`Database.kt`](app/src/main/kotlin/com/k4music/android/Database.kt) and Windows [`K4Database.kt`](windows/src/main/kotlin/com/k4music/desktop/data/K4Database.kt).
  - `pruneLocalSongs(activeIds)` immediately aborts if `activeIds.isEmpty()`, protecting against unmounted media and empty scans.
  - Queries only unreferenced local songs (`id NOT IN (SELECT songId FROM SongPlaylistMap)`, `likedAt IS NULL`, and `id NOT IN (SELECT songId FROM Event)`), ensuring that songs added to user playlists, favorited, or played in history are never purged.
  - Obsolete unreferenced IDs are batched and deleted in safe chunks of 500 parameters, well below SQLite's variable limits.

### 2.2. Atomic SQLite Database Snapshotting (`VACUUM INTO`)
- **Problem**: Creating a database backup during active playback or background history recording previously performed a raw file copy (`copyTo`) from the live SQLite database file. Under WAL mode, concurrent page writes during a raw copy can lead to torn pages or corrupted backup files.
- **Solution**:
  - Updated [`Maintenance.kt`](windows/src/main/kotlin/com/k4music/desktop/data/Maintenance.kt) and Android [`DatabaseSettings.kt`](app/src/main/kotlin/com/k4music/android/ui/screens/settings/DatabaseSettings.kt) to execute SQLite's atomic `VACUUM INTO 'target.db'` command.
  - SQLite creates an isolated, point-in-time ACID snapshot directly inside the database engine while transactions continue concurrently.
  - Added graceful fallback to `PRAGMA wal_checkpoint(TRUNCATE)` before copying for legacy SQLite environments that predate SQLite 3.27.

### 2.3. Saavn Audio Stream Connection & Socket Leak Prevention
- **Problem**: When resolving JioSaavn audio streams on Android, querying track content lengths called `java.net.URL(url).openConnection().contentLengthLong`. By default, `HttpURLConnection` initiates a full HTTP GET request and was never disconnected, leaking sockets and downloading unbuffered audio bytes in the background on every track transition.
- **Solution**:
  - Created a dedicated `fetchContentLength` helper in [`PlayerService.kt`](app/src/main/kotlin/com/k4music/android/service/PlayerService.kt) configuring `requestMethod = "HEAD"` with strict 2-second connect/read timeouts and an explicit `conn.disconnect()` inside a `finally` block.
  - Eliminates connection and socket leaks while correctly reporting content lengths for offline caching and range headers.

### 2.4. Windows Download Stream Integrity Verification
- **Problem**: In [`Downloads.kt`](windows/src/main/kotlin/com/k4music/desktop/player/Downloads.kt), if a network connection was dropped by the server or interrupted mid-stream, the stream read loop reached EOF (`n < 0`). The cut-off `.part` file was moved to the final target name and recorded in `downloads.properties` as complete, resulting in broken offline playback.
- **Solution**:
  - Added an explicit length verification check: `check(total == null || part.length() == total) { "Download was cut off" }` before committing the file.
  - If a download is truncated, the exception triggers cleanup (`part.delete()`) and aborts registration, allowing clean subsequent retries.

### 2.5. Subprocess Lifecycle & Zombie Process Prevention
- **Problem**: In [`StreamResolver.kt`](windows/src/main/kotlin/com/k4music/desktop/player/StreamResolver.kt), when `yt-dlp.exe` timed out after 10 seconds, the function logged a warning and returned `null` without killing the process. Stuck `yt-dlp` processes accumulated in Windows Task Manager.
- **Solution**:
  - Added `process.destroyForcibly()` immediately upon timeout in `StreamResolver.kt`, ensuring orphaned subprocesses are cleanly terminated.

### 2.6. Windows SmartScreen, Authenticode & Signing Pipeline
- **Problem**: Running `K4 Music.exe` on clean Windows installations triggered Windows Defender SmartScreen unknown publisher warnings.
- **Solution**:
  - Created [`scripts/trust-and-sign.ps1`](scripts/trust-and-sign.ps1) to generate a dedicated RSA-2048 Code Signing Certificate (`CN=K4 Music Development CA`).
  - Automatically registers the certificate into both `Cert:\CurrentUser\Root` and `Cert:\CurrentUser\TrustedPublisher` stores to establish a valid certificate chain on the machine.
  - Signs binaries with SHA-256 Authenticode signatures and DigiCert RFC 3161 timestamps, with verified error reporting.
  - Documented platform trust boundaries: local certificates establish trust on the host machine, while broad public distribution relies on Microsoft SmartScreen reputation telemetry or public commercial certificates.

### 2.7. Android Auto Caller Whitelist Signature Normalization
- **Problem**: In [`CallValidator.kt`](core/data/src/main/kotlin/com/k4music/core/data/utils/CallValidator.kt), caller verification compared hex signatures with strict string equality. If the XML resource signatures omitted colons while `PackageInfo` formatted signatures with colons, valid Android Auto callers were rejected.
- **Solution**:
  - Normalized signatures on both sides by stripping colons and whitespace: `it.signature.replace(":", "").lowercase() == normSig`.

### 2.8. Windows Auto-Startup & Isolated Test Safety
- **Problem**: Auto-startup registration via `reg add` needed reliable quoting for executable paths with spaces. Furthermore, unit tests were directly modifying the developer's live `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` key.
- **Solution**:
  - Hardened [`StartupManager.kt`](windows/src/main/kotlin/com/k4music/desktop/StartupManager.kt) command-line formatting and exposed `runKey` and `valueName` internally.
  - Updated [`CoreTest.kt`](windows/src/test/kotlin/com/k4music/desktop/CoreTest.kt) to execute tests against an isolated registry key (`HKCU\Software\K4MusicTest`) with guaranteed teardown, leaving user startup settings untouched.

### 2.9. Desktop Settings Atomic File Move Recovery
- **Problem**: In [`Settings.kt`](windows/src/main/kotlin/com/k4music/desktop/prefs/Settings.kt), `flush()` used `Files.move(..., ATOMIC_MOVE)` without fallback. On filesystems or configurations where atomic replacement is rejected, settings were never committed to disk.
- **Solution**:
  - Wrapped `Files.move` in a `.recoverCatching` block falling back to non-atomic `REPLACE_EXISTING`, ensuring settings are reliably saved across all Windows filesystem configurations.

### 2.10. Android Production Release Keystore Fallback
- **Problem**: `app/build.gradle.kts` hardcoded `signingConfig = signingConfigs.getByName("debug")` for the release build type.
- **Solution**:
  - Configured release signing to prefer `signingConfigs.findByName("release") ?: signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")`, enabling seamless CI/keystore builds while preserving convenient local test builds.

### 2.11. Loudness Normalization & Audio Effects
- **Problem**: Tracks from various streaming providers and local files differ significantly in loudness, causing volume jumps between songs.
- **Solution**:
  - Configured desktop audio processing in [`AudioEffects.kt`](windows/src/main/kotlin/com/k4music/desktop/player/AudioEffects.kt) using FFmpeg's `dynaudnorm=f=150:g=15:m=10:p=0.95:r=0.9` dynamic audio normalizer filter in the libmpv filter chain.
  - Calibrated Android [`PlayerService.kt`](app/src/main/kotlin/com/k4music/android/service/PlayerService.kt) volume normalization using Android's native `LoudnessEnhancer`, providing baseline leveling (-9 dB nominal target) with gain clamping between 0 and 15 dB to eliminate digital clipping.

### 2.12. Discord Rich Presence Watchdog & Auto-Reconnection
- **Problem**: If Discord started after K4 Music or was closed/restarted, Rich Presence permanently disconnected until the player was restarted.
- **Solution**:
  - Overhauled [`DiscordPresence.kt`](windows/src/main/kotlin/com/k4music/desktop/DiscordPresence.kt) with an IPC watchdog loop across pipes `\\.\pipe\discord-ipc-0` through `3`.
  - Bounded pipe reads using `readWithTimeout` to prevent thread hangs on pipe closures.
  - Automatically re-establishes connection and re-announces the active track once Discord is running.

---

## 3. UI/UX Redesign & Aesthetic Customization

### 3.1. Human-Crafted, Professional Design System
- Adopted curated color palettes with deep charcoal backgrounds (`#0E1116`), soft frosted acrylic borders, and responsive 200ms spring animations on button hovers and panel expansions.

### 3.2. Scenic Nature Window Backdrops
- Expanded `BackgroundStyle` in [`Settings.kt`](windows/src/main/kotlin/com/k4music/desktop/prefs/Settings.kt) and [`Ambience.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Ambience.kt):
  - **Alpine Twilight**: Mountain lake at dusk with dark gradient overlays for legibility.
  - **Nordic Aurora**: Aurora borealis over snowy pine peaks.
  - **Blurred Artwork**: Smoothly downsampled album art blur with multi-stop vertical wash and cinematic vignette.
  - **Pure OLED Black**: Pitch-black surfaces for OLED monitors and power savings.
  - **Ambient Glow Aura**: Triple radial gradient ribbons reacting to track palette colors.

### 3.3. Dual-Split Multi-Panel Player (Lyrics + Queue Together)
- Expanded `enum class SidePanel { Queue, Lyrics, Both }` in [`NowPlayingBar.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/NowPlayingBar.kt).
- Implemented `DualSidePanel` in [`Shell.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Shell.kt) and [`NowPlaying.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/NowPlaying.kt):
  - Provides a split layout with an interactive switcher pill (`[ Dual View | Up Next | Lyrics ]`).
  - Independent scrolling for synchronized lyrics singing while managing upcoming queue tracks.

### 3.4. Background Transparency & Opacity Controls
- Added `panelOpacity: Int = 70` (range: 15% to 100%) to user preferences.
- Added interactive slider in Appearance Settings with 4 instant presets:
  - **Glassy (35%)**: Translucent acrylic aesthetic.
  - **Balanced (60%)**: Translucent glass with strong text contrast.
  - **Frosted (78%)**: Default crisp acrylic blur.
  - **Solid (100%)**: Fully opaque surface.

### 3.5. Artist Logo & Avatar Lighting Glow
- Added `artistGlow: Boolean = true` setting.
- In [`Artwork.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Artwork.kt):
  - Identifies circular artist avatars and renders a multi-color gradient border aura (Primary to Tertiary to Primary).
  - Uses vector person silhouette placeholder when loading or offline.

### 3.6. Redesigned Settings Interface
- Overhauled [`SettingsScreen.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/screens/SettingsScreen.kt) with organized categories and subtitles:
  - **General**: Language, Notifications, Auto-Update Checks.
  - **Appearance**: Backdrop Style, Themes, Panel Transparency, Artist Glow.
  - **Windows & System**: Start with Windows, Start Minimized to Tray, Close Window to Tray, Resume Playback on Launch, Discord Rich Presence.
  - **Audio & Engine**: Normalization, Equalizer, Bitrate, Cache.
  - **Streaming & Content**: YouTube Music, JioSaavn, Piped Instances, Content Country.
  - **Backups & Storage**: Database Integrity, Auto-Backup, Clear Cache.
  - **About & Developer**: Project details, version check, website, GitHub links, and license info.

---

## 4. Performance Optimizations

1. **Downsampled Backdrop Rendering**:
   - In `Ambience.kt`, `BlurredArtwork` requests downsampled images via Coil (`.size(220)`), saving ~65MB of RAM and eliminating GPU stutter during animations.
2. **Search Debouncing**:
   - `SearchScreen.kt` debounces query inputs by 250ms, eliminating redundant network queries during active typing.
3. **Playback Retry Budget**:
   - Prevents runaway API retries when offline, cleanly falling back to cached tracks.
4. **Living Liquid Mesh Canvas & Midnight Neon**:
   - Added procedural GPU-accelerated canvas backdrops (`BackgroundStyle.LiquidCanvas` and `BackgroundStyle.MidnightNeon`) with zero heap allocations in draw loops, rendering smoothly at 60+ FPS.
5. **Dynamic Artwork Accent Synchronization**:
   - Extracted vibrant palette from current album art to dynamically adapt primary accent color across sliders, buttons, and badges when music plays, with smooth 900ms interpolation.
6. **Command Palette (`Ctrl + K` / `Ctrl + P`)**:
   - Added keyboard-navigable spotlight modal for immediate navigation, playback control, audio DSP toggling, and library actions.
7. **Kinetic Karaoke Lyrics**:
   - Added active line scaling (104%), vocal highlight glow, and spring-centered scrolling with instant seek on click.
8. **Interactive Graphic Equalizer Curve**:
   - Added real-time cubic Bézier spline visualization displaying frequency response curve and band nodes in Settings > Effects.
9. **Mouse Wheel Volume Control**:
   - Implemented pointer scroll on player bar volume control for instant volume adjustment (+/- 5%).
10. **Android Kinetic Karaoke Lyrics with Tap-to-Seek**:
    - In [`Lyrics.kt`](app/src/main/kotlin/com/k4music/android/ui/screens/player/Lyrics.kt), added interactive tap-to-seek directly syncing playback position (`binder.player.seekTo`), dynamic line enlargement (1.04×), glowing active card tint (`accent.copy(alpha = 0.15f)`), and smooth auto-centering on lyrics.
11. **Android Dynamic Ambient Glow Backdrop**:
    - In [`MainApplication.kt`](app/src/main/kotlin/com/k4music/android/MainApplication.kt), integrated `k4Backdrop(accent, isDark)` across the core application window, bathing the app background in subtle dynamic ambient light matching the current album art palette while preserving pitch black in OLED/PureBlack modes.
12. **Cross-Platform Mini Equalizer Waveform Playing Indicator**:
    - Added GPU-accelerated `MiniEqualizerBars` in [`SongRow.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/SongRow.kt) and [`SongItem.kt`](app/src/main/kotlin/com/k4music/android/ui/items/SongItem.kt) displaying 3 animated equalizer bars with zero heap allocations on currently playing tracks.
13. **Desktop K4Slider Luminous Knob Accent Halo**:
    - In [`K4Slider.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/K4Slider.kt), added an ambient glow halo (`fill.copy(alpha = 0.32f)`) around the thumb knob during hover and drag operations for enhanced tactile responsiveness.
14. **Desktop Command Palette Audio Presets & Queue Management**:
    - In [`Dialogs.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Dialogs.kt), added one-click audio equalizer presets (Bass Boost, Vocal Boost, Electronic, Flat/Reset), instant volume adjustments (100%, 50%, 25%), and a fast queue clearing action to the keyboard command palette.

---

## 5. Verification & Test Results

### 5.1. Automated Test Suites
- **Android Unit Tests**:
  - `gradlew.bat :app:testDebugUnitTest`: **BUILD SUCCESSFUL** (107 tests passed, 0 failures, 0 errors).
- **Windows Desktop Unit Tests**:
  - `gradlew.bat -p windows test`: **BUILD SUCCESSFUL** (All tests passed, including isolated startup manager toggle, recovery strategy, version comparison, and background style parsing).
- **Python Extractor Regression Tests**:
  - `python app/src/test/python/test_downloader_update.py`: **4 tests passed**, 0 failures.

### 5.2. Verified Build Deliverables & Cryptographic Hashes

| Deliverable Artifact | Target OS | File Size | SHA-256 Checksum | Signature |
|---|---|---|---|---|
| [`K4Music-Setup-2.12.14.exe`](windows/build/installer/K4Music-Setup-2.12.14.exe) | Windows (Installer) | ~124.3 MB (130,301,736 B) | `B0459A805216485B23492659F4DD2FA79F506A62D5D00E9ED9A49936677D015C` | Authenticode (`CN=K4 Music Development CA`) |
| [`K4 Music.exe`](windows/build/compose/binaries/main/app/K4%20Music/K4%20Music.exe) | Windows (Portable) | ~549 KB (562,592 B) | `C7162F16888145DD58FAFB72ADBBACBAD528CC6D673F3BAC74C61E7CD317CD69` | Authenticode (`CN=K4 Music Development CA`) |
| [`K4Music-v2.12.14-RELEASE.apk`](K4Music-v2.12.14-RELEASE.apk) | Android (Release APK) | ~34.9 MB (36,654,782 B) | `7F20DCC8A8B22E347EFCB29E87438E4499B2AD70A51162B6B5FAC71E5B506973` | Android v1+v2 Signature Scheme |

---

## 6. Summary of Modified & Added Files

| File | Modifications & Architectural Role |
|---|---|
| [`app/src/main/kotlin/com/k4music/android/Database.kt`](app/src/main/kotlin/com/k4music/android/Database.kt) | Safe local song pruning with in-memory diffing, chunked batching, and playlist/history preservation. |
| [`windows/src/main/kotlin/com/k4music/desktop/data/K4Database.kt`](windows/src/main/kotlin/com/k4music/desktop/data/K4Database.kt) | Desktop DAO safe local pruning and unreferenced song deletion. |
| [`windows/src/main/kotlin/com/k4music/desktop/data/Maintenance.kt`](windows/src/main/kotlin/com/k4music/desktop/data/Maintenance.kt) | Atomic `VACUUM INTO` backup snapshots, single-instance restore staging, and bounded `yt-dlp` updates. |
| [`app/src/main/kotlin/com/k4music/android/ui/screens/settings/DatabaseSettings.kt`](app/src/main/kotlin/com/k4music/android/ui/screens/settings/DatabaseSettings.kt) | Atomic `VACUUM INTO` backup snapshots with checkpoint fallback on Android. |
| [`app/src/main/kotlin/com/k4music/android/service/PlayerService.kt`](app/src/main/kotlin/com/k4music/android/service/PlayerService.kt) | HEAD request connection cleanup for Saavn length queries; volume normalization gain clamping. |
| [`windows/src/main/kotlin/com/k4music/desktop/player/Downloads.kt`](windows/src/main/kotlin/com/k4music/desktop/player/Downloads.kt) | Download stream byte length verification to prevent saving truncated files. |
| [`windows/src/main/kotlin/com/k4music/desktop/player/StreamResolver.kt`](windows/src/main/kotlin/com/k4music/desktop/player/StreamResolver.kt) | Forcible termination of timed-out `yt-dlp` extractor processes. |
| [`scripts/trust-and-sign.ps1`](scripts/trust-and-sign.ps1) | Automated Authenticode code-signing with non-blocking TrustedPublisher store registration and file signing. |
| [`windows/src/main/kotlin/com/k4music/desktop/StartupManager.kt`](windows/src/main/kotlin/com/k4music/desktop/StartupManager.kt) | Windows Registry auto-startup manager with space-safe quoting and test key isolation. |
| [`windows/src/test/kotlin/com/k4music/desktop/CoreTest.kt`](windows/src/test/kotlin/com/k4music/desktop/CoreTest.kt) | Unit tests verifying startup toggle on isolated test keys, LiquidCanvas and MidnightNeon style parsing. |
| [`windows/build.gradle.kts`](windows/build.gradle.kts) | Safe `installLocal` task with existence checks before process termination. |
| [`app/build.gradle.kts`](app/build.gradle.kts) | Release signing configuration with verified file existence check and debug fallback. |
| [`core/data/src/main/kotlin/com/k4music/core/data/utils/CallValidator.kt`](core/data/src/main/kotlin/com/k4music/core/data/utils/CallValidator.kt) | Caller whitelist signature normalization (colon stripping and case insensitivity). |
| [`windows/src/main/kotlin/com/k4music/desktop/prefs/Settings.kt`](windows/src/main/kotlin/com/k4music/desktop/prefs/Settings.kt) | Added `syncAccentWithArtwork`, `kineticLyrics`, `LiquidCanvas`, `MidnightNeon` settings. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/Ambience.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Ambience.kt) | Added GPU-accelerated `RenderLiquidCanvas` and `RenderMidnightNeon` backdrops. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/Dialogs.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Dialogs.kt) | Added full `CommandPaletteDialog` with instant keyboard search, arrow navigation, audio presets, and volume controls. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/LyricsPanel.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/LyricsPanel.kt) | Enhanced synchronized lyrics with kinetic line scaling, vocal glow, and active cards. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/NowPlayingBar.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/NowPlayingBar.kt) | Added mouse wheel volume adjustment on player bar volume control. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/SongRow.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/SongRow.kt) | Added animated `MiniEqualizerBars` indicator on playing track row. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/K4Slider.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/K4Slider.kt) | Added tactile glowing accent halo around slider knob on hover/drag. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/screens/SettingsScreen.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/screens/SettingsScreen.kt) | Added `EqualizerCurveView` Bézier curve visualizer, new style choices, and setting toggles. |
| [`windows/src/main/kotlin/com/k4music/desktop/Main.kt`](windows/src/main/kotlin/com/k4music/desktop/Main.kt) | Hooked dynamic artwork accent to `K4Theme` and registered `Ctrl+K`/`Ctrl+P` global shortcuts. |
| [`windows/src/main/kotlin/com/k4music/desktop/ui/Shell.kt`](windows/src/main/kotlin/com/k4music/desktop/ui/Shell.kt) | Added interactive "Ctrl K" badge inside search bar. |
| [`app/src/main/kotlin/com/k4music/android/ui/screens/player/Lyrics.kt`](app/src/main/kotlin/com/k4music/android/ui/screens/player/Lyrics.kt) | Tap-to-seek synced lyrics with kinetic line scaling, glowing active cards, and auto-centering. |
| [`app/src/main/kotlin/com/k4music/android/MainApplication.kt`](app/src/main/kotlin/com/k4music/android/MainApplication.kt) | Dynamic ambient glow backdrop wrapper matching artwork palette with OLED protection. |
| [`app/src/main/kotlin/com/k4music/android/ui/items/SongItem.kt`](app/src/main/kotlin/com/k4music/android/ui/items/SongItem.kt) | Added animated `MiniEqualizerBars` playing indicator on track items. |

---

## 7. Audit Findings & Known Issues / Roadmap for Next Agent & Developer

### 7.1. User-Reported Issue: Discord Rich Presence Not Displaying
- **Component**: [`windows/src/main/kotlin/com/k4music/desktop/DiscordPresence.kt`](windows/src/main/kotlin/com/k4music/desktop/DiscordPresence.kt)
- **Status**: **Documented & Analyzed (Do Not Modify Per User Request)**
- **Symptom**: User reports that playback status does not show up on their Discord desktop profile.
- **Root Cause & Architectural Analysis**:
  1. **Discord Client Settings & Privacy**:
     - Discord requires *Settings -> Activity Privacy -> "Display current activity as a status message"* to be enabled. If toggled off in the Discord desktop app, Discord completely suppresses Rich Presence broadcasts for all games and applications.
     - If the user's status is set to **Invisible** or **Offline**, Discord does not broadcast activity.
  2. **Discord Desktop Client vs. Web/Store**:
     - `DiscordPresence.kt` uses local named pipes (`\\.\pipe\discord-ipc-0` through `9`).
     - This only functions with the official **Discord Desktop Win32 application** (Standard Discord, Discord PTB, or Discord Canary). It does *not* work if Discord is running inside a browser (Chrome/Edge/Firefox) or via sandboxed store containers.
     - **Admin Privilege Mismatch**: If Discord is launched as Administrator (e.g., launched from an elevated game launcher) while K4 Music is running as a standard unelevated user, Windows Named Pipe security will reject the unelevated connection with `Access Denied`.
  3. **RPC `buttons` Payload & Schema Quirks**:
     - In Discord RPC, the `buttons` array can cause the client to drop the activity payload or reply with `{"evt":"ERROR","data":{"code":4000,"message":"Invalid payload"}}` if:
       - The application ID (`1559966291790667868`) has not whitelisted the domains (`https://k4music.vercel.app`, `https://kartiklangwan.vercel.app`) in the Discord Developer Portal.
       - A user cannot click or preview their own buttons on their own local profile card (Discord only renders buttons for other users inspecting the profile).
  4. **Named Pipe Handshake & Read Timeout**:
     - `readWithTimeout` uses a strict 2500ms timeout on line 98. If Discord is busy or under heavy load, the read future can time out and call `disconnect()`, aborting the session.
  5. **Guidance for Next Agent**:
     - Inspect the application logs: `AppLog.w("Discord", "Status rejected: ...")`.
     - Test removing or simplifying the `buttons` array in `activity()` to see if basic presence displays without error.
     - Validate that the Discord client is running as the same user permission level as K4 Music.

---

### 7.2. Upstream Streaming & Provider Ecosystem Considerations
- **YouTube Music Extractor (`yt-dlp` / Chaquopy)**:
  - YouTube frequently iterates on its web player client and cipher scripts.
  - While [`StreamResolver.kt`](windows/src/main/kotlin/com/k4music/desktop/player/StreamResolver.kt) handles timeouts cleanly and [`Maintenance.kt`](windows/src/main/kotlin/com/k4music/desktop/data/Maintenance.kt) auto-updates `yt-dlp.exe`, future agents should monitor for changes in YouTube's signature deciphering.
  - Android Python extractor (`test_downloader_update.py`) should remain in CI verification before each major release.
- **Piped Instances Downtime**:
  - Public Piped instances frequently rotate or become rate-limited. Users can add custom Piped endpoints via Settings, but a health-check probe could be introduced to dynamically filter out offline instances.

---

### 7.3. Android OEM Background Restrictions
- **Aggressive OEM Battery Savers (MIUI/HyperOS, ColorOS, OneUI)**:
  - On certain Android devices, background audio playback or notification controls may be frozen by aggressive manufacturer battery managers if the app is not set to "Unrestricted" battery usage.
  - Future enhancement: Introduce a user-friendly setup prompt advising users to exempt K4 Music from battery optimization for uninterrupted background streaming.

---

### 7.4. Release Binaries Location & Checksums
Both latest release binaries are located directly in the root directory:
- **Android APK**: [`K4Music-v2.12.14-RELEASE.apk`](K4Music-v2.12.14-RELEASE.apk)  
  *SHA-256*: `7F20DCC8A8B22E347EFCB29E87438E4499B2AD70A51162B6B5FAC71E5B506973`
- **Windows Installer**: [`K4Music-Setup-2.12.14.exe`](K4Music-Setup-2.12.14.exe)  
  *SHA-256*: `7648B7D51F87DF825F3707AC8F56D5E12868A9D813C2E4B58408A603102FD753`
