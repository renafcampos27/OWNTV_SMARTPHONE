# Feature parity with the television — final pass

**Status: complete. Every row below was checked against the code in this repository on 2026-09-14,
not against a plan or a changelog.**

The design mockup that governed Plan 4 carried a *Feature parity checklist*: every feature of
[OwnTV for Android TV](https://github.com/ahXN00/OwnTV), with a status of **Port**, **Redesign**,
**New** or **Dropped**. Phase 15 of the build plan required a final pass over it — every row
accounted for, every drop justified in writing. This is that pass.

Two rows ended somewhere other than where the mockup put them. Both are explained below, under
[Deviations](#deviations-from-the-mockup).

---

## Direct port — same behaviour, new layout

| Feature | Done | Where |
|---|---|---|
| Xtream / M3U / Stalker sources | ✅ | Setup flow and Settings → Playlists; all parsing is core's |
| Multiple playlists | ✅ | Playlists page; the provider name shows on rows, which matters more here than on a television |
| Catch-up & live rewind | ✅ | The same `LiveLadder` as the television; a drag replaces the D-pad |
| Pause and rewind live TV (saved copy) | ✅ | Core's `TimeshiftManager`; rewind/forward buttons, bar, Go live, resume popup; PiP and sound-only keep saving |
| Series detail, seasons, episodes | ✅ | Opens on the last-watched episode |
| Autoplay next episode / season | ✅ | Core logic, untouched. **Previous / Next** buttons joined the transport row on 2026-09-17 — until then the next episode was reachable only from the card in the last thirty seconds |
| Resume / watch history | ✅ | Core. The **Resume playback** setting (Always / Ask / Never) was displayed but never read until 2026-09-17, so the phone always resumed silently; all three modes now behave as the television's, including its ten-second threshold |
| Favourites | ✅ | Core, plus a Favourites screen under More |
| Home rows, order, hidden set | ✅ | All six `HomeRow` values |
| Trending (TMDB) | ✅ | Core, with a detailed-card or posters-only choice |
| Search | ✅ | Grouped results with a count per kind. Scrolling to the end loads the next page — the television still stops at the first 40 of each kind |
| Downloads | ✅ | With SAF for the folder, as the mockup required |
| Customize categories & items | ✅ | Including bulk rename, spans and the PIN lock |
| Profiles, PIN, restrictions | ✅ | Plus a profile picture from the phone's photos, which the television has no way to offer |
| Backup / restore | ✅ | Including the television→phone migration path, encrypted backups included |
| Theme, accent, Glass Effect | ✅ | The same stored values; a colour picker was added for all three colour settings |
| App icon (eight colours) | ✅ | The same stored choice and the same restart question; the phone has no banner or TV home channel |
| Locales | ✅ | 26 packaged, inherited from core's resources — the mockup said 24, and the catalogue has grown since |
| Player: engine ladder, mpv/Exo | ✅ | `:player-core`, untouched, including the per-channel compatibility pin. The phone ran **half** the ladder until 2026-09-14 — one engine was watched and the other was a terminus. It now walks core's own `LiveLadder`: four engine/format rungs, each at most once, with the whole-tune budget behind Settings → "Give up after" |
| External player handoff | ✅ | Richer here than on the television, because a phone has more players installed |
| Weather | ✅ | Core, in °C or °F |
| **In-app update check** | ✅ | **Settings → App.** See [Deviations](#deviations-from-the-mockup) |

## Redesigned — same function, different shape

| Feature | Done | Shape it took |
|---|---|---|
| Live TV browse | ✅ | Three panels became chips + list + detail |
| EPG / Guide | ✅ | The grid was kept, and *On now* added as the portrait default; Timeline is a third shape |
| Movies / Series browse | ✅ | One Library tab with a segmented control |
| Long-press menus | ✅ | Bottom sheets, with the same actions in the same saved order |
| Settings — 10 groups | ✅ | A tile grid became a list with drill-down; the search box was kept |
| Player: subtitles, zoom, speed, volume boost | ✅ | The same functions on a gesture *and* a button surface — no gesture-only function. The panels are sheets rather than centred dialogs, and **they scroll**: until 2026-09-17 a sheet taller than a sideways phone silently dropped its last rows, which hid A/V sync, subtitle timing and subtitle search. The audio button is also always on the bar, as the television's is, so A/V sync is reachable on a single-soundtrack film |
| Mini player / audio bar | ✅ | Three choices, not one: a floating window, a docked bar, or off |

## New — no television equivalent

| Feature | Done | Where it lives |
|---|---|---|
| System Picture-in-Picture | ✅ | `PipController`, with a window size and edge snapping of the user's own |
| Audio focus (calls, other apps) | ✅ | `PlaybackSession` in `:player-core` — shared, so the television gained it too |
| Headphone / Bluetooth routing | ✅ | `ACTION_AUDIO_BECOMING_NOISY` in the same file |
| Background playback + media notification | ✅ | `MediaSession`, with lock-screen controls and ±10 s |
| Screen-off behaviour | ✅ | Audio continues, the video surface is released; remembered per channel. **It was true for films and downloads only until 2026-09-14** — live channels play on the other engine, and this asked the first one, which is stopped there. Every lifecycle rule now asks whichever engine holds the stream |
| Mobile data vs Wi-Fi | ✅ | Data-saver behaviour and download-on-Wi-Fi-only |
| Portrait layouts | ✅ | Every screen; tablets and landscape get the two-pane variants |
| Predictive back gesture | ✅ | `android:enableOnBackInvokedCallback="true"` |
| SAF storage picker | ✅ | `MediaTarget` / `MediaRoot` in core; no `MANAGE_EXTERNAL_STORAGE` anywhere |

## Dropped — and why

Each of these is a television feature with nothing on a phone for it to be. None was dropped for
being difficult, and none is deferred: the ship-complete policy applies here too.

| Feature | Why it cannot exist here |
|---|---|
| **Panel width adjustment** | It sets the relative width of side-by-side panels. A phone shows one column at a time, so there is nothing to apportion. The tablet two-pane layouts use a fixed split, which is the right answer for a screen that is sometimes a phone. |
| **Remote shortcuts** | Colour keys, channel-number entry and the D-pad ladders exist because a remote control has those buttons. There is no remote. Every function they reached is reachable by tap or long-press — that was checked feature by feature, not assumed. |
| **Watch Next / launcher channels** | An Android TV launcher feature. This app has no `LEANBACK_LAUNCHER` entry, so a row it published would be a row nothing could open. `CoreBuildInfo.tvHome = false` turns it off in core rather than leaving the publish to fail quietly, and the two TV-provider permissions are explicitly removed from the merged manifest. |
| **Auto frame-rate switching** | **Not dropped after all** — see below. |

---

## Deviations from the mockup

**1. The in-app update check was nearly dropped, and should not have been.**

The mockup listed it as a **Port**, with the note *"kept while sideloaded; removed if Play ships"*.
During the build it was written out anyway: `SettingsAppPage` carried a comment saying there would
never be an update check here, the manifest listed `REQUEST_INSTALL_PACKAGES` among permissions this
app would never declare, and `CHANGELOG_APP.md` said the app had no update sheet. The reasoning was
Google Play — the same reasoning that correctly chose SAF over `MANAGE_EXTERNAL_STORAGE`.

It was restored in Phase 15, as the mockup always intended. Distribution is sideloaded, so nothing
else was ever going to tell anyone a new version existed. The permission is declared for the updater
and nothing else, and the manifest says so. **The mockup's own condition still stands: if this app
ever ships on Play, the permission and the two Settings rows come out together.**

Worth keeping as a lesson: three files had been made to agree with a decision, which made it look
far more settled than it was. Only the mockup disagreed, and the mockup was right.

**2. Auto frame-rate switching was dropped, then built.**

The mockup dropped it on the grounds that phone panels do not switch refresh rate for content. Some
now do. It exists as `autoFrameRate` on the Playback settings page, **off by default**, and it warns
before turning on where the display cannot be asked — which is the honest shape for a setting whose
hardware support cannot be detected reliably. Since P10 (2026-09-23) it acts: full screen only, seamless
switches only (owner decision 7) — through the same core surface hint the television uses.

---

## One gap, deliberately left open

The television has a **Check for update** *quick toggle* — an action tile that can be pinned to the
top of Settings. This app's quick toggles are switches only (`QuickToggle` carries a `Flow<Boolean>`
and a setter), so an action tile would mean a second kind of quick toggle for a single use.

**Check updates on startup** is a switch and could be made pinnable if anyone asks for it. Nobody
has. The check itself is one tap away on the App page and is findable through the Settings search
box, which is where the television's tile leads anyway.

## Signals core 1.0.49 added, supplied here too

Core gained two signals a host app raises from its own screens. Both are supplied.

| Signal | Where | What it does |
|---|---|---|
| `WatchSession` | `PlayerScreen` | Opens while a channel, film or episode from a playlist is playing, so core's background Stalker catalogue drain steps aside. A downloaded file carries no `sourceId` and holds no session — it spends no provider connection |
| `CatalogPriority` | `LibraryViewModel.select` | Names the category the user just opened, so the drain fills that one next instead of following the provider's order |

Neither changes whether the catalogue ends up correct, only when. On a provider that allows one
stream at a time, the first is what stops a background download competing with the picture.
