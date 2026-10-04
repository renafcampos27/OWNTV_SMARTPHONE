<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="brand/app-logos/logo_eggshell_light.png">
    <img src="brand/app-logos/logo_eggshell.png" alt="OwnTV Mobile" width="300">
  </picture>
</p>

<h1 align="center">OwnTV Mobile — Master Product Brief</h1>

<p align="center">
  <b>Native Android IPTV player for phones and tablets · dual playback engine · Material 3</b><br>
  <sub>Bring your own M3U, Xtream or Stalker/Ministra portal sources</sub>
</p>

---

## 1. Overview

OwnTV Mobile is the phone and tablet member of the OwnTV family: a native Android IPTV player written
in Kotlin with Jetpack Compose and Material 3, sharing its **entire engine** with
[OwnTV for Android TV](https://github.com/ahXN00/OwnTV) through the
[OwnTV core library](https://github.com/ahXN00/OwnTV_Core).

**Two interfaces, one database, one sync, one playback stack.** Everything below the screen — the
Room schema and every migration, playlist parsing, EPG, backup, profiles, downloads, recording,
settings storage, both playback engines and **every translated string** — is core's, shared byte for
byte with the television.

It is a **player only**: you supply an Xtream login, an M3U playlist (URL or a local file) or a
Stalker/Ministra portal.

**Key principles**

| | |
|---|---|
| **Player only** | No bundled content, no subscriptions, no paywalls |
| **Touch-first** | Gestures, a document picker, the share sheet, rotation, a keyboard that covers half the screen |
| **Not the TV app relaid out** | Material 3 components, not a ten-foot focus ladder |
| **One engine, two shells** | A behaviour fixed here is fixed on the television, and the reverse |
| **Language-first** | 26 packaged interface languages, chosen before anything else |
| **Open source** | GPLv3, original code, built with the help of AI |

### 1.1 What is deliberately absent

| Not here | Why |
|---|---|
| `androidx.tv.material` | Its components assume a focus-driven, ten-foot interface |
| D-pad focus engineering | Focus still matters for keyboards and accessibility, but the ten-foot focus ladder is a TV answer to a TV problem |
| `MANAGE_EXTERNAL_STORAGE` | A phone has a document picker — SAF is used instead |
| Watch Next / launcher channels | An Android TV surface; there is no leanback launcher entry here |
| Panel width adjustment | A phone shows one column at a time; the tablet two-pane split is fixed |
| Remote shortcuts | There is no remote. Every function they reached is reachable by tap or long-press |

`REQUEST_INSTALL_PACKAGES` **is** declared, for the in-app updater and nothing else — distribution is
sideloaded, so nothing else can tell a user a new version exists. If the app ever ships on Google
Play, that permission and the two update rows in Settings come out together.

---

## 2. Playback

### 2.1 Dual-engine design

The same two engines as the television, behind the same `PlaybackEngine` interface in
`:player-core`.

| Engine | Role |
|---|---|
| **libmpv (FFmpeg)** | Films, series, recordings, downloads, and any live stream ExoPlayer cannot open |
| **ExoPlayer / Media3** | Live TV by default, and every Multiview tile |

- **Engine preference per section**, globally and **per playlist** — either engine first with the
  automatic handover, or one engine only with it switched off.
- **Track memory** — the audio and subtitle language picked in the player is remembered per
  channel, film and series (per profile).
- **Per-playlist provider quirks** — catch-up time zone, "give up after" time and an HTTP Referer.
- **Per-channel compatibility mode** — the **⇄** button pins a channel to mpv and remembers it,
  using the same store the television uses, so a pin syncs between the two devices.
- **The fallback ladder** — core's `LiveLadder`: up to four rungs, each tried once,
  `ExoPlayer+HLS → ExoPlayer+TS → mpv+HLS → mpv+TS` or the same led by mpv. Both engines are watched:
  `LiveExoWatchdog` for one, a 35-second open watcher for the other. **Give up after** bounds the
  whole tune, and a provider back-off does not count against it.
- **Protected (DRM) channels** play on ExoPlayer, which outranks every other preference. They
  **cannot be recorded** — the CDM decrypts only into a secure decoder for immediate display, so a
  recording is refused before it starts rather than left as a file that will not play.
- **Container is decided from evidence**, not from the file extension: the playlist's declared
  `manifest_type`, then what the response actually turns out to be, then what the same provider has
  already been caught serving. HLS, MPEG-DASH and raw MPEG-TS. The response sniff is the only route
  available to Stalker portals and Xtream panels, whose stream addresses carry no declaration.

### 2.2 The player surface

A full-screen route rather than an activity, so going full screen and coming back is a change of view
and never a restart. It asks for landscape on arrival and then lets go of the orientation again.

**Gestures — and every one has a button.**

| Gesture | Does |
|---|---|
| Tap | Controls on/off |
| Double-tap left/right | Skip by the seek step (or the live rewind step) |
| Drag sideways | Scrub, with the target shown before you let go |
| Drag vertically, outer thirds | Brightness (left) · volume (right) |
| Swipe down/up, middle third | Mini player · channel list |
| Pinch | Zoom — fit or fill |
| Press and hold | 2× speed while held |
| Two-finger tap | Mute |

Gestures are classified **once per gesture** in a single pointer loop, rather than stacked detectors
that consume each other's events. **Gesture sensitivity** scales how far a value moves, not how far
the finger must travel to be recognised, so a drag is still a drag at 50%.

The transport row carries **Previous · Rewind · Play/Pause · Forward · Next**, with the two episode
buttons present only when the engine reports an episode on that side — never on a film or a live
channel. Every tool panel is a bottom sheet capped at the landscape-aware sheet height and scrolled
inside it, because the player is always landscape and an uncapped sheet drops its last rows rather
than growing.

### 2.3 The mobility layer — what only a phone has

- **Picture-in-Picture** — leaving the app while the picture is full screen carries it into the
  system's own floating window, shaped to the picture, with skip and play/pause.
- **The app's own floating window** — draggable, pinch-resizable across three sizes, edge-snapping,
  double-tap to expand, long-press for its menu, swipe down to stop. Its position is deliberately not
  stored: a window the user drags has no setting to set.
- **Docked mini player** — the alternative, as a bar above the tabs. Sound-only and casting always
  use it, because there is no picture for a window to hold.
- **Sound only** — drops the video decoder and keeps the audio, with artwork and a sleep timer. It
  can arm itself when the screen goes off or on mobile data, and remembers the choice per channel.
- **Background playback** — a `mediaPlayback` foreground service hung on core's `PlaybackSession`
  token, so the lock screen, the notification and the media buttons are the session's own rather than
  a second copy that can disagree with it.
- **Audio focus for a phone** — a call **pauses** the film rather than playing it quietly under the
  caller, and unplugging headphones stops it instead of switching to the loudspeaker. Both differ
  from the television's defaults on purpose.
- **Casting (Google Cast)** — sender-side only, and absent from the television by nature: an Android
  TV box *is* a receiver. `CastPlaybackEngine` is another `PlaybackEngine`, so the lock screen and
  notification followed it for free. A stream a receiver cannot decode gets a readable, translated
  refusal rather than a silent failure.

### 2.4 Subtitles & audio

Text, image (PGS/VOBSUB/DVB) and closed captions, with size, **font**, colour, position and
background; the chosen font file is handed to the engine so mpv draws it too. OpenSubtitles search
and local files, a timing nudge, preferred audio and subtitle languages (per profile, 50 languages, plus "Original
language" from TMDB), A/V sync with a
remember-this-delay, surround handling and 150% volume boost, Night mode and Volume leveling (both
engines), a Dolby/DTS passthrough switch, Maximum video quality with a mobile-data limit and a per-item
Quality button, and experimental tunneled playback — all core's, all shared.

---

## 3. Browse

### 3.1 Shell & navigation

A bottom bar on a phone, a navigation rail from **600 dp**, chosen by **window width** rather than
device type — so a phone in landscape and a tablet in split screen are treated as what they are. From
**840 dp**, Live TV, the Library and Settings become **two panes**, with Settings keeping its own
route stack so a page opens beside the list instead of replacing it.

A collapsing top bar carries search, the cast button and the playlist chip. Tapping or long-pressing
the current tab returns its list to the top. Predictive back is on.

### 3.2 Sections

- **Home** — a snapping full-width hero rail, **Now Trending** as either the detailed hero or a plain
  poster row, continue-watching rows, and favourite/recent channel rails in **Cards** or **On now**.
  Anything part-watched started from here obeys core's **Resume playback** mode — Always, Ask or
  Never — through one shared gate, so the six routes into a saved position cannot disagree.
- **Live TV** — category chips with a search sheet, channel rows with number, logo, now/next,
  progress, provider name and catch-up and favourite markers. A channel opens its own screen on a
  phone; on a tablet it plays in the pane beside the list.
- **Library** — Movies and Series as one screen with a segmented control, **pinch to resize** the
  grid, and a detail page with backdrop, chips, resume, favourite, download, season chips and episode
  progress.
- **Guide** — **three shapes the user picks between and the app remembers**: *On now* (the portrait
  default), *Grid* (the landscape and tablet default) and *Timeline*. A phone loads one day, one row
  at a time as the row appears; the television's whole-lineup window would be thousands of rows to
  draw twelve.
- **Search** — one debounced field over channels, films and shows, grouped with counts, with recent
  terms and three curated chips. Results page as the list is scrolled rather than stopping at the
  shared reader's first forty of each kind.
- **⋯ More** — Downloads, Recordings, Favourites, History, Backup, Local sync, Profiles, the error
  log, About and Settings.

### 3.3 Long-press, everywhere

Seventy long-press handlers across twenty-one files — channels, films, shows, episodes, categories,
downloads, recordings, guide rows, search results, playlists, EPG sources, profiles, quick toggles,
the floating window and the navigation bar. The menus use core's own action keys and the user's saved
order, so both apps offer the same actions in the same arrangement.

---

## 4. EPG, catch-up & recording

The guide, catch-up, live rewind, auto-matching, guide offsets and multiple XMLTV feeds are core's
and behave as they do on the television — including **Pause and rewind live TV** (a channel without
catch-up saved on the phone while watched, kept through picture-in-picture and sound-only playback).
Mobile-specific shapes:

- The **three guide views** above, with a day strip, a jump-to-now button and a category filter.
- A **programme sheet** whose synopsis is fetched when it opens, carrying watch, watch-from-start,
  record, record-every-showing and favourite.
- **Recording** from the guide, the channel list or the player, with the status pill appearing over
  the player in recordings-only mode so a running recording is visible during playback.
- **Multiview** — up to four tiles, drawn through a **TextureView** rather than a SurfaceView,
  because a device has very few hardware video planes and a second SurfaceView gets audio and no
  picture. Landscape puts two per row; portrait stacks them.
- **Connection limits** — measured once at a playlist's first sync and used to refuse a tile or a
  recording with a sentence rather than a failure.

---

## 5. Storage, downloads & sync

- **Downloads and recordings to a folder the user picks**, through SAF — `MediaTarget` and
  `MediaRoot` in core. Chosen over `MANAGE_EXTERNAL_STORAGE` because a media player will not be
  granted it on Google Play. **Export** moves a finished file anywhere, asking for a *persistable*
  grant so the row still points at something playable next week.
- **Wi-Fi-only downloads** and a **data saver** that refuses to start a stream on mobile data.
- **Backup & restore** — core's `.own` container, optionally encrypted, through the phone's own file
  picker, with a PIN-gated profile checklist.
- **Local sync** — the two devices pair over the LAN (QR code scanned with the camera, or picked from
  network discovery), preview exactly what a merge will change, and apply it deliberately. Hosting
  runs only while the screen is open. Deletions propagate as deletions. It is also offered on the
  **first setup screen**, where a phone with nothing on it only receives and never hosts, so a new
  device is furnished from the television without going near the menus.

---

## 6. Profiles & settings

Profiles with PINs, kids mode, and **a photo of the user's own** as an avatar — something the
television has no way to offer. A profile gate that survives rotation but not process death.

**Settings is three levels**: root → group → leaf, with a registry that drives the routes, the
breadcrumbs, the back titles and the search index together, so a page can never be reachable by one
and invisible to another. A search field over every setting; **quick toggles the user builds by
long-pressing any switch to pin it**; sub-pickers as bottom sheets, because a sheet is the one form
that frosts the app's own wallpaper.

Every value is read and written through core's `SettingsRepository`, so a backup taken here means the
same thing on a television.

---

## 7. Surface language

The app draws **every icon itself** on the same 24×24 grid as the television's set, so the two look
like one product; nothing depends on the stock Material glyph set. Material 3 for everything
touchable.

**Glass** is rendered here rather than merely tinted: a RenderEffect backdrop blur (API 31+), a
specular rim, layered depth, continuous corners, settling motion and a wallpaper-sampled tint, with
**Aurora** as an additional preset. The stored model stays core's and unchanged, so a television user
is unaffected.

The wallpaper and its blurred copy sit **outside** the shell, so the frost every panel samples is one
image for the whole app rather than one per panel.

---

## 8. Language & accessibility

26 packaged interface languages, chosen on the first screen, with RTL-aware layouts. **This
repository contains no `strings.xml` of its own and must not grow one** — a string added here would
never reach Weblate and would be English-only for ever, so even a mobile-only string is added in
core.

A consumer-side gate enforces it: a hardcoded literal fails the compile rather than the push, and its
baseline is **empty**, so there is no recorded debt for a new literal to hide behind. The only two
exits are core's `strings_*.xml` or a declared technical literal.

Accessibility work covers state as well as labels — a tick and an accent colour say "selected" to an
eye and to nothing else, so selection is announced.

---

## 9. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.20 |
| Build | AGP 9.4.0 / Gradle 9.7.1 |
| UI | Jetpack Compose, Material 3, Compose BOM 2026.09.00, adaptive layouts |
| Engine | `tv.own.owntv:core` + `:player-core` — Room, sync, EPG, backup, libmpv and Media3 |
| Media | Media3 1.11.1 (the subtitle view only; the player itself is core's) |
| DI | Koin 4.2.2 |
| Networking / images | OkHttp 5 · Coil 3.6.2 |
| Casting | Google Cast 22.3.1 + MediaRouter |
| Camera / QR | CameraX 1.6.2 + ZXing (pairing for local sync) |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37`, `applicationId tv.own.owntv.mobile` |

`applicationId` differs from the television's on purpose, so both install side by side on one device
and each keeps its own data. Both apps sign with the **same keystore** — a certificate is stable per
`applicationId`, not unique per app.

---

## 10. Architecture

### 10.1 Three repositories

| Repo | Holds |
|---|---|
| **[OwnTV_Core](https://github.com/ahXN00/OwnTV_Core)** | Database, sync, EPG, backup, profiles, downloads, recording, settings storage, both engines, all strings |
| **[OwnTV](https://github.com/ahXN00/OwnTV)** | The Android TV interface |
| **[OwnTV_Mobile](https://github.com/ahXN00/OwnTV_Mobile)** | This app — the phone and tablet interface |

A core change is made in core, verified against the television before it is released, and taken here
by an automatic pin-bump pull request. Nothing is worked around locally: a workaround here is a bug
the television will hit again.

### 10.2 This repository's shape

```
app/src/main/java/tv/own/owntv/mobile/
├── ui/screens/      home · live · library · guide · search · downloads ·
│                    recordings · multiview · settings
├── ui/player/       the full-screen player, gestures, sheets, mini players
├── ui/components/   the shared surface language — buttons, rows, sheets, glass, icons
├── ui/shell/        the bar/rail shell, the top bar, the sync pill
├── playback/        the foreground service, PiP, the data-saver gate (the sleep timer is core's)
├── cast/            the Google Cast sender
└── di/              Koin modules
tools/i18n/          the locale catalogue and the consumer-side string checks
extras/              the guide, this brief, the parity checklist, logos and screenshots
```

### 10.3 Two tuners, one surface

`LiveTuner` and `VodTuner` outlive every screen deliberately: a channel screen, the full-screen
player and the mini player are three views of **one** stream, so the tuning state cannot belong to a
view model that dies with its route. Only an explicit stop ends playback. There is one engine and one
surface, so the two tuners can never both be playing — the VOD one stops the live one before it
starts.

### 10.4 Build & release

ABI flavours (`standard` for real devices, `x86_64` for emulators), R8 with keep rules on release,
locale filtering driven by a catalogue file that is deliberately duplicated from core because Gradle
reads it at configure time, a baseline-profile module, and CI that builds, lints, runs the string
gates and publishes a signed release from a tag.

---

## 11. Legal

OwnTV is a media **player** only. It ships with no channels, playlists, subscriptions or content, and
does not endorse or facilitate access to unauthorized streams. Users are solely responsible for the
sources they add.

Released under the **GNU General Public License v3.0**.

Metadata and trailers from [TMDB](https://www.themoviedb.org/) (not endorsed or certified by TMDB).
Subtitles from [OpenSubtitles](https://www.opensubtitles.com/) (not endorsed or certified by
OpenSubtitles). Translations hosted free of charge by
[Hosted Weblate](https://hosted.weblate.org/projects/owntv/).
