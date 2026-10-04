# OwnTV Mobile — User Guide

Everything the phone and tablet app can do, **where to find it**, and the gestures that make it fast.

Each entry is one line about what it does and one line about where it lives. Skim the headings and
stop where something looks useful.

> **The basics:** tap to open · **long-press** almost anything for its menu · swipe back to go up.
> Long-press is where favourites, rename, hide, record, catch-up and download live.
>
> **No gesture is the only way to do something.** Every gesture in the player also has a button.

---

## 🚀 Start here

A fresh install walks you through the same steps as the television:

```
Welcome  →  Text size  →  Disclaimer  →  Set up OwnTV  →  Profile  →  Add a playlist  →  Import
```

1. **Welcome** — the language picker is on this screen: one of 26, or **System default**. Tap
   **Get started**.
2. **Text size** — interface zoom and font size, with a sample that resizes as you adjust, and the
   **App icon** colour.
3. **Disclaimer** — OwnTV is a player; you bring the sources.
4. **Set up OwnTV** — **New profile**, **Restore a backup**, or **From another device** (copy
   everything off a television you already have — see below).
5. **Profile** — name, avatar, and optionally a **Kids** profile or a PIN.
6. **Add a playlist** — **New**, **Existing** (only when another profile already has one), or
   **Import** a backup file. **Skip for now** is a valid answer.
7. **New** opens the form straight away — Xtream, M3U or Stalker. A phone has a keyboard, so there is
   no remote-entry step here; that exists on the television, and your phone is what fills it in
   (see [Setting up the television too?](#-setting-up-the-television-too)).
8. **Import** runs. Unlike the television, the phone does **not** offer the TV guide afterwards —
   add it yourself at [Settings → Sources → EPG sources](#-add-guide-data).

### 📺 Coming from the television?
**Where:** step 4 → **From another device**, or **Restore a backup**
No file needed for the first one: on the television open **⋯ More → Local sync** and turn **Sync
mode** on, then pick **From another device** here. The phone finds it on the network or scans its
code, you type the 6-digit PIN once, tick what to bring across, and see exactly what will change
before it changes. A phone being set up only ever *receives* — nothing on the television is altered.
A backup made on the TV also restores here, encrypted ones included — and **Restore a backup** asks
what to bring back before it applies anything, so you can take the playlists and leave the old
device's settings behind. Later on, the full two-way version lives at
⋯ More → [Local sync](#-local-sync-with-your-tv).

---

## 🧭 Getting around

**On a phone** — a bottom bar: Home · Live TV · Guide · Library · ⋯ More.
**On a tablet, or a phone held sideways** — the bar becomes a rail down the left, with more room, and
Live TV, the Library and Settings open **two panes** side by side.

- **Tap the tab you are already on** — or long-press it — to jump that list back to the top.
- **⋯ More** holds Downloads, Recordings, Favourites, History, Backup, Local sync, Profiles, the
  error log, About and **Settings**.
- The **playlist chip** in the top bar switches which playlist the whole app is showing.

---

## 👆 Player gestures — the cheat sheet

| Gesture | Does |
|---|---|
| **Tap** | Show or hide the controls |
| **Double-tap left / right** | Skip back / forward by your seek step — on a live channel with catch-up, by your **Live rewind step** |
| **Drag sideways** | Scrub. The bar shows where you will land before you let go |
| **Drag up/down, left third** | Brightness |
| **Drag up/down, right third** | The phone's volume (same as the volume keys); past the top, a boost up to 150% |
| **Swipe down, middle** | Shrink into the mini player, still playing |
| **Swipe up, middle** | The channel list (live only) |
| **Pinch** | Zoom — fit or fill |
| **Press and hold** | 2× speed while your finger is down |
| **Two-finger tap** | Mute / unmute |

**Too sensitive?** **Settings → Playback → Mobile → Gesture sensitivity** changes how far a value
moves per centimetre of finger, without changing how far you must travel to start a gesture.

---

## 📥 Playlists & sources

### ➕ Add a playlist
**Where:** Settings → Sources → **Playlists** → Add, or the first-run wizard
**Xtream** (server, user, password), **M3U** (a URL, or a file picked with your phone's file picker),
or **Stalker/Ministra** (portal URL + MAC, with optional Serial Number, Device IDs and Signature).
Under **User-Agent** there is an optional **Referer** — leave it empty unless your provider asks
for one; it is then sent with every stream of that playlist.

### 📱 Setting up the television too?
**Where:** on the **TV** — Settings → Manage sources → Add source → **Remote**
Typing an Xtream password with a TV remote is miserable, so the television can hand the job to this
phone. It shows a QR code and a 6-digit PIN; scan it, fill the form in your browser, and press
**Send to TV** — then press **Start Import** on the television. It is a web page served by the TV, so
it works from any phone on the same Wi-Fi, this app installed or not.

### ℹ️ Info & Re-test
**Where:** Settings → Sources → Playlists → tap a playlist → **Info**
Whether the account is alive, when it expires, and **how many streams your provider allows** —
measured once at the first sync. **Re-test** re-measures it; it warns first, because it stops
anything playing and takes a few minutes.

### ⚡ What to sync, and running it in the background
**Where:** while adding, and Settings → Sources → Playlists → Edit
Choose per section whether Live, Movies and Series are fetched. A big import offers **Run in
background** so you can start using the app immediately; a status pill shows progress.

### 🗂️ Several playlists
**Where:** the playlist chip in the top bar
Show them all merged, or narrow the whole app to one. Rows carry the provider's name when more than
one is loaded.

### 📡 Stalker portals
Behave like any other playlist once added. If the portal refuses the login, re-check the MAC **and
your phone's date & time** — Stalker validates timestamps.

---

## 📺 Live TV

### ▶️ Watch a channel
**Where:** Live TV → tap a channel
A phone opens the channel's own screen — the picture in a 16:9 box with the guide and sibling
channels below. **Tap the picture**, or turn the phone sideways, for full screen. On a tablet the
channel plays in the pane beside the list.

### ⭐ The long-press menu
**Where:** long-press any channel
Favourite · Rename · Hide · Match EPG · EPG offset · Move · Move to category · **Record** ·
**Add to Multiview** · Catch-up · Play in another app. Inside a custom category: **Remove from this category**.

### 🏷️ Categories
Chips above the list, with a search button for providers with hundreds of them.
**Long-press a category chip** to hide or move it without going to Settings.

### 🔧 Compatibility mode
**Where:** full-screen player → the **⇄** engine button
If a channel stutters or won't open, one press flips it to the other engine and remembers that
channel's choice.

### 🏛️ Which engine channels start on
**Where:** Settings → Playback → Video player → Player → **Live TV player**
**ExoPlayer, then mpv** (default) · **mpv, then ExoPlayer** · **ExoPlayer only** · **mpv only**, with
a **per playlist** override below it.

> **A channel that won't play is worked through every combination** — each engine on each stream
> format, up to four, each tried once. Then it stops and tells you.
> **Give up after** (Settings → Playback → Video player → Live tuning) bounds how long that may take, and
> **Give up after, per playlist** gives one provider its own time.

---

## 🗓️ TV Guide — three shapes

**Where:** the **Guide** tab → the **tune** button (top right) → View

| Shape | Best for |
|---|---|
| **On now** | A phone held upright — one channel per row, what's on, how far through, what's next |
| **Grid** | A tablet or a phone sideways — the classic guide, with a shared timeline |
| **Timeline** | One channel read top to bottom |

The app remembers which you chose. Also in that sheet: **sort**, **Auto-match EPG** with a review
list — tick **Include guide logos** there and the matched channels also take the guide's logo — and a **Size** slider for the grid's time scale. A day strip sits above the guide — as many days
as **Guide days to keep** is set to — and there's a search box.

**Tap a programme** for its synopsis, **Watch channel**, **Watch from start** (catch-up),
**Record**, **Record every showing** and **Favourite**.

### ➕ Add guide data
**Where:** Settings → Sources → **EPG sources**
**The guide is opt-in, and the phone never offers it by itself** — if you have no programme names,
this is why. Add XMLTV feeds here, **Fill from playlist** to take the URL your playlist already
carries, set a User-Agent, pick a refresh interval — which can be **every N days** — and choose
whether to use that feed's channel logos.

**Guide days to keep** is under Settings → Sources & guide → EPG, beside EPG time offset: how many days of upcoming guide to store, 1–14, seven
by default. The same number decides how much is downloaded, how much is kept, and how many days the
strip above the guide offers. Old programmes are kept only on channels with catch-up, since those are
the only ones that can play them back.

A large guide takes a while, and it **keeps downloading when the screen goes off** — a notification
shows while it does, so the phone lets it finish instead of stopping it and starting it over.

> **Playlist has no channel logos?** Turn on **Use this guide's channel logos** on the feed. Logos
> then come from the XMLTV guide instead; channels the feed has no logo for keep the playlist's one.

---

## ⏪ Catch-up & ⏺️ recording

### ▶️ Replay something
**Where:** Guide → a past programme → **Watch from start**, or long-press a channel → **Catch-up**

### 🕐 Rewind live
**Where:** full-screen player, on a channel with an archive
Drag the live bar back, tap the rewind button, or use **Go back to…** for a list of times. **Go live**
returns.

### ⏸️ Pause and rewind channels without catch-up
**Where:** **Settings → Playback → Video Player Settings → Live TV → Pause and rewind live TV**
Off by default. While you watch a channel full screen, OwnTV saves it on this phone and plays it
from that copy, so it still looks live — and you can **pause**, rewind and go forward on channels
whose provider keeps no archive. Pause, the rewind button, forward (once you are behind), the bar and **Go live** work as on a catch-up channel. A dark stretch on the bar is a moment the connection
dropped; playback jumps over it.

- **Rewind length:** 15 (default), 30, 45 or 60 minutes. At least **1 GB** of the phone's storage always
  stays free; the oldest part goes first.
- **Picture-in-picture and sound-only playback are not leaving: they keep saving.**
- **The delete rules:**
  - Leave the channel and its copy is kept for **5 minutes**. Come back within them and OwnTV asks
    **Continue where you left off?** — **Resume** plays on from where you left, **Go live** jumps to now.
    **Resume a saved channel**, just below, turns the question into **Always resume** or **Never resume**.
  - Watch another channel for **2 minutes** and the copy you left is deleted at once — you have moved on.
  - After 5 minutes it is deleted anyway, and every copy is deleted when OwnTV starts.
- Catch-up channels still rewind into the provider's archive. Protected (DRM) and encrypted channels
  play as before, without a copy.

### ⏺️ Record
**Where:** long-press a programme in the Guide, or a channel in the list, or the **Record** button in
the player
**Record every showing** sets a standing rule. Recordings land in **Downloads → Live TV**, and the
status pill shows one running even over the player.

> A recording costs one of your provider's connections and says so before it starts.

> **Copy-protected channels cannot be recorded.** Where a provider protects a channel with DRM,
> it can be watched but not saved — the protection is theirs, not OwnTV's. Asking to record one
> stops straight away and tells you why, rather than leaving a file that will not play.

---

## 🔲 Multiview

**Where:** the **Multiview** button in the player, or long-press channels → **Add to Multiview**
*(Turn it on first: Settings → Playback → Video player → Multiview.)*

| Gesture | Does |
|---|---|
| **Tap a tile** | Give it the sound |
| **Tap an empty tile** | Pick a channel |
| **Double-tap** | That channel, full screen |
| **Long-press** | The tile menu — change channel, sound only, add a tile, remove |
| **Back** | Leave, stopping everything |

Landscape puts two tiles per row; portrait stacks them, which is honest rather than clever. The
Settings number is a **ceiling**, not a size — the grid opens with two and grows when you ask.
Leaving the app stops the tiles.

---

## 🎬 Library — films & shows

### 🖼️ Browse
**Where:** the **Library** tab
Movies and Series as one screen with a segmented control (a tablet gets them as separate rail items).
**Pinch to resize** the posters, or use the **Size** slider.

### ▶️ A film or a show
Tap for its page: backdrop, cast, chips, **Resume** or **Play**, favourite, download, season chips
and episode progress. A show **opens on the episode you last watched**.

Starting something part-watched anywhere else — a **Continue watching** row, the hero card, an
episode in a series — asks **Resume or start over?** Settings → Video player → Resume & auto-play → **Resume playback**
changes that to always resuming, or always starting from the beginning. A film you left in the first
ten seconds never asks.

### 🔧 The long-press menu
Favourite · Download · Hide · Rename · Move · Move to category · Mark watched/unwatched ·
TMDB details · Trailer · Play in another app.

---

## 🎬 TMDB metadata

**Where:** Settings → Content → **Metadata**
Posters, plots, cast and trailers. **Metadata source** picks provider-only, provider + TMDB, or TMDB
only; **Language** sets the language of plots and artwork, separately from the app's own language.

**Your own key** removes the shared allowance — the page shows what is left of it and has a **Test
connection** so you can prove a key or a self-hosted server works.

---

## 🔎 Search · 🕐 History · ⭐ Favourites

### 🔎 Search
**Where:** the magnifying glass in the top bar
One field over channels, films and shows, grouped with a count for each. Recent searches are kept,
and three chips offer **Continue watching**, **Unwatched favourites** and **Channels**.
Long-press a result to favourite, download or hide it.
Scroll to the bottom and more results load by themselves, so a word your provider carries hundreds
of — "CNN", say — doesn't need a narrower search to reach the rest.

### ⭐ Favourites & 🕐 History
**Where:** ⋯ More → Favourites / History
Long-press a history row to remove it; **Clear** wipes it by type.

---

## 📥 Downloads & recordings

**Where:** ⋯ More → **Downloads**
Three tabs — **Live TV** (your recordings), **Movies**, **Series** — with a free-space bar, a
measured transfer rate, and pause · resume · retry · delete per item. Downloads keep running when you
leave the app.

### 📂 Choose where they are saved
**Where:** Settings → Content → Downloads → **Download folder**
Pick any folder with your phone's own folder picker, an SD card included. **Export** copies a
finished download or recording anywhere you like. If that folder goes missing — a card removed, or
permission withdrawn — downloads go to OwnTV's own folder until it is back, and the Downloads screen
says so.

### 📶 Wi-Fi only
**Where:** Settings → Network
**Download on Wi-Fi only**, and a **data saver** that refuses to start a stream on mobile data.

---

## 🎛️ The player

Every gesture above also has a button on the control bar:

| Button | What |
|---|---|
| **Go live** | Back to the live edge |
| **Volume · Brightness** | Sliders, with a mute row |
| **Speed** | 0.5× to 2× |
| **Subtitles · Audio** | Tracks (the language you pick is remembered per channel, film or series), plus subtitle search, subtitle timing and A/V sync. Both are always on the bar — a film with one soundtrack still has A/V sync. Scroll the panel to reach what is below the tracks |
| **Previous · Next** | The episode either side of this one, beside play. Series only |
| **Aspect** | Fit · Fill · Stretch · Original · Force 16:9 · Force 4:3 |
| **Quality** | Only when the stream offers several picture sizes. Auto, or one size for what is playing now |
| **Favourite** | Adds what is playing |
| **Catch-up** | *Go back to…* on archive channels |
| **Previous channel** | Back to the channel you watched before; tap again to flip back. Headphone "previous" does the same on live |
| **⇄** | Swap the player engine |
| **Channels** | The channel list, categories first |
| **Mini player** | Shrink and keep browsing |
| **Sound only** | Drop the picture, keep the sound |
| **Record · Multiview** | Once enabled in Settings |
| 🌙 **Sleep timer** | 15–90 min, end of programme on a live channel, or end of movie / episode (the next episode then does not start); green while running. Stopping playback yourself cancels it. **Also turn off the screen** locks the phone when it ends — Android asks for permission once |
| **Info** | The technical readout (on mpv including **Interlacing**: none, deinterlaced by the player or by the phone, or not deinterlaced) — and **Report** appears while it is open |

**Back stops the stream.** The mini-player button and the swipe down are what keep it playing.

### 🪟 The mini player
**Where:** Settings → Playback → Mobile → **Mini player**
- **Floating window** (default) — drag it anywhere, **pinch** to resize between three sizes, it snaps
  to the edges, **double-tap** to go full screen, **long-press** for its menu, swipe it down to stop.
- **Docked bar** — above the tabs, with the title and transport buttons.

Sound-only and casting always use the docked bar: there is no picture for a window to hold.

### 📱 Picture-in-Picture
Press **Home** while watching full screen and the picture follows you into the system's own floating
window, over other apps, with skip and play/pause buttons. Turn it off in
**Settings → Playback → Mobile**.

### 🎧 Sound only
**Where:** the headphones button in the player, or the floating window's menu
Keeps the sound, drops the picture — the phone's biggest battery and data saving. It can switch
itself on **when the screen goes off** or **on mobile data**, and it remembers your choice per
channel. A **sleep timer** lives in the same menu — and on the full-screen player bar.

---

## 📺 Cast to a television

**Where:** the cast button in the top bar or the player
Appears only when there is a receiver on your network. The television plays the stream itself, so
the phone's lock screen and notification then drive *it*.

> The receiver decodes the stream, and a Chromecast cannot play raw MPEG-TS — a large share of an
> IPTV catalogue. OwnTV says so plainly rather than failing silently.

---

## 🔄 Local sync with your TV

**Where:** ⋯ More → **Local sync**
Swap playlists, profiles, favourites, history and resume positions with the television over your own
Wi-Fi. No account, no cloud, no file.

```
Both devices open Local sync  →  pair (scan the QR, or pick from the list)
          →  choose a direction: send · receive · merge
          →  preview exactly what will change  →  apply
```

- **Both devices must have the screen open.** Hosting runs only while it is, which is what keeps it
  cheap and deliberate. The exception is a phone still being set up: it offers this during setup and
  only receives, so only the *other* device needs Sync mode on.
- **Nothing is applied on arrival** — you see what will change first.
- **A deletion stays deleted** on both devices, rather than being undone by the merge.

---

## 👥 Profiles

**Where:** ⋯ More → **Profiles**
Each has its own favourites, history, resume points and layout. Add a **PIN**, or make it a **kids
profile** to hide adult content everywhere. Pick one of ten drawn avatars — or **a photo of your
own**, which the television cannot offer.

**Where a profile opens:** Settings → App → **Start on** — Home, the last channel, Favourites, or one
chosen channel.

---

## 🎨 Make it yours

### 🎨 Theme, accent & glass
**Where:** Settings → **Appearance**
Light, dark or system, with an accent colour. Three colour settings — accent, selection highlight and
subtitle text — each open a **real colour picker**: hue bar, saturation square, live preview and a
hex box.

**App icon** (Settings → App): eight colours for the OwnTV icon and logo — Petrol,
Sunflower, Cobalt, Tomato, Station Board, **Eggshell** (default), Olive and Olive on Cream. **Restart
now** switches at once: OwnTV closes and opens again by itself, on Home, with the new colour.
**Later** switches when you next leave the app. Some launchers take a moment
to show the new icon, and a few move it from the home screen to the app list.

**Glass Effect** has its own page: presets including **Aurora**, transparency, frost, and which
surfaces get it. Real frost needs Android 12+.

### 🔤 Fonts & size
**Where:** Settings → Appearance → **Fonts**, and the zoom/text sliders
Six font families for the interface and for popups, and interface zoom.

### 🗂️ Customize
**Where:** Settings → Content → **Customize categories**
Hide, rename, reorder and regroup categories and items, with **bulk rename** (rules, automatic
cleanup, a review step and restore-originals), **custom categories**, span selection, and an optional
**PIN lock**.

### 🏠 Home & layout
**Where:** Settings → Layout → **Home**
Reorder or hide Home rows, switch channel rows between cards and *On now*, and choose whether Now
Trending is the detailed hero or a plain poster row.

---

## ⚙️ Settings worth knowing

Settings is a list of groups, each opening its own page. There's a **search box** at the top, and
**Quick toggles** you build yourself by **long-pressing any switch to pin it**.

| Setting | Where | Why |
|---|---|---|
| **Live TV player** (+ per playlist) | Playback → Video player → Player | Which engine opens a channel |
| **Give up after** (+ per playlist) | Playback → Video player → Live tuning | Bounds how long a dead channel can spin |
| **Movies & Series player** (+ per playlist) | Playback → Video player → Player | Which engine opens films and episodes |
| **Catch-up time zone per playlist** | Sources & guide | One provider's archive on a different clock (quarter-hour zones included) |
| **Live latency** (+ per playlist) | Playback → Video player → Live tuning | Closer to live, or steadier |
| **Pre-buffer** (+ per playlist) | Playback → Video player → Live tuning | Collect a few seconds first on a flaky provider |
| **Multiview** | Playback → Video player → Multiview | Off by default; also sets the tile ceiling |
| **Record what I'm watching** | Playback → Recording | Adds the Record button to the player |
| **Auto frame rate** | Playback → Video player → Picture | Off by default; full screen, seamless switches only; warns where the display can't be asked |
| **Film buffer** · **Network timeout** · **Reconnect attempts** | Playback → Video player → Streaming | Films, episodes and catch-up on a bad line. Auto / 1 = as before |
| **Reset saved live TV player choices** · **Forget learned stream fixes** | Playback → Video player → Player | Undo per-channel player choices; forget what the player learned about a provider |
| **Dolby and DTS passthrough** · **Night mode** · **Volume leveling** | Playback → Video player → Sound | Send Dolby/DTS undecoded (on) or decode in the app; turn loud scenes down; even out loudness between channels. Night mode and leveling work on both engines |
| **Maximum video quality** · **Maximum quality on mobile data** | Playback → Video player → Picture | The highest picture played when a stream offers several; the mobile-data limit follows a Wi-Fi ⇄ mobile switch while playing |
| **Tunneled playback** | Playback → Video player → Picture | Experimental, off. Only on phones that support it; turns itself off after a failure |
| **Preferred audio / subtitle language** | Playback → Video player → Languages & subtitles | Per profile, 50 languages. **Original language** (audio) plays a film or series in the language it was made in, when the stream has it |
| **Subtitle appearance** | Playback → Subtitle appearance | Size, **font**, colour, position, background |
| **Gesture sensitivity** | Playback → Mobile | How far a value moves per centimetre of finger |
| **Background playback** | Playback → Mobile | Keep the sound when the app leaves the screen |
| **Data saver** | Network | Refuse to stream on mobile data |
| **Check for updates** | App | On startup, or on demand |
| **Error log** | ⋯ More → Error log | Recent playback failures, exportable |

### 💾 Backup & restore
**Where:** ⋯ More → **Backup & Restore**
One `.own` file with your profiles, sources, settings and downloaded subtitles, **optionally
encrypted with your own password**. Choose which profiles travel. Restores a television backup too.

---

## 🩺 Troubleshooting

| Problem | Try this |
|---|---|
| A channel stutters or won't open | The **⇄** button in the player — compatibility mode |
| Channel won't play at all | It already tried every engine and format. See ⋯ More → **Error log** |
| "Too many connections" | Wait — OwnTV counts down and retries by itself |
| The screen keeps sleeping | Only if nothing is playing; check the channel actually started |
| Stalker portal refuses the login | Re-check the MAC and your phone's **date & time** |
| Guide is blank | Settings → Sources → **EPG sources** — add a feed, sync it, then **Auto-match** |
| Downloads won't start | Settings → Network — **Download on Wi-Fi only** may be on |
| Nothing casts | The receiver has to decode it; raw MPEG-TS channels cannot be cast |
| Update won't install | Android asks you to allow installs from OwnTV the first time |

**Still stuck?** ⋯ More → **Error log** exports a report. Bring it to
[t.me/owntvplayer](https://t.me/owntvplayer).

---

## 💡 Tips

- **Long-press** is the answer to "where is that option?" nine times out of ten.
- **Swipe down** in the player keeps the stream; **Back** stops it. That is the whole difference.
- Turn the phone **sideways** on a channel screen to go full screen without reaching for a button.
- **Pin your own Quick toggles** — long-press any switch in Settings.
- A **free personal TMDB key** removes the shared metadata limit.
- If you also use the television, **Local sync** saves setting everything up twice.
