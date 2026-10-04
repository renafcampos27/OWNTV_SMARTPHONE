# OwnTV Mobile — Changelog

> Release notes: two parts per version — New features (by name) and Fixes. This file feeds the GitHub release body and the in-app update sheet.
>
> **Rule: bullet points only — no descriptions.** Each line is a short bolded feature/fix title and
> nothing more. The only extra detail ever allowed is a contribution credit for community work.
> Issue numbers that are part of a title are fine; explanatory parentheticals are not. The detail —
> what, why, files and verification — belongs in the commit message, never here.

## Unreleased

### New features
- **🎨 Pixel app icon and Plus Jakarta Sans, the new default font**
- **🔊 Volume swipe controls the phone's volume**
- **⏪ Resume a saved channel: ask, always or never**
- **🗂️ Remove a channel from a custom category**

### Fixes
- **Custom categories in the player's channel list and the Multiview picker**
- **Hidden and renamed categories respected in the player's channel list**
- **Hiding a provider category no longer empties custom categories**
- **Catch-up that shows no picture no longer reports a fast-start error**
- **Faster channel switching on providers that mix HLS and TS channels**

## v1.0.3 — 2026-09-26

### New features
- **🖼️ Match EPG can bring the guide's logo**
- **⚙️ Video player settings open on a list of categories**

- **⏸️ Pause and rewind live TV on every channel**
- **🎨 New icon and logo, in eight colours**
- **😴 Sleep timer in the full-screen player, and it can switch the screen off**
- **📶 Film buffer, network timeout and reconnect attempts**
- **👄 A/V sync works on every player**
- **⏮️ Previous channel**
- **🎞️ Auto frame rate now works**
- **🧹 Reset live player choices, forget learned stream fixes**
- **🕒 Catch-up time zone in quarter hours**
- **🎚️ Four more settings per playlist**
- **🔗 Referer for a playlist that needs one**
- **🗣️ The player remembers your audio and subtitle language**
- **🌐 Preferred languages per profile, more languages, and "Original language"**
- **▶️ Catch-up carries on to the next programme**
- **🔊 Dolby and DTS passthrough, Night mode and Volume leveling**
- **📺 Maximum video quality, a mobile-data limit and a Quality button**
- **🧪 Tunneled playback (experimental)**
- **🎛️ A newer mpv player**

### Fixes

- **⚙️ Hardware decoding, Stereo only and default zoom apply from the first channel**
- **💯 "Above 100%" shows one percent sign**
- **🎞️ Deinterlacing is automatic**
- **🌈 HDR says it is mpv only**
- **📱 Settings speak about your phone, not a television**
- **🧭 Per-playlist pickers open their second step**
- **📐 The add-playlist form clears the status bar**
- **🧰 Player memory moved into the app's database**
- **🌐 Older language codes match**
- **📺 Channels you set to ExoPlayer on the TV open on ExoPlayer here too**
- **🛠️ Diagnostics, catch-up and restored player settings now work on the phone**
- **🔐 Restoring or syncing from another device keeps this phone's playback setup**
- **👥 A restore never hands one person's data to another profile**
- **📱 Multiview no longer closes when the screen times out**
- **🧠 Memory pressure reaches every player**
- **🔎 Settings search finds more settings**
- **💾 Backups keep Multiview and recording settings**
- **🌐 Custom DNS actually works — and all three presets pass**
- **🔋 The player does less work**
- **🔁 Quick channel picks always end on the last one**
- **⏪ Catch-up from the Guide is never overtaken by the live channel**
- **🚫 A channel that cannot open leaves the screen as it was**
- **⏩ Double tap on live uses your Live rewind step**
- **🔗 Portal channels recover when their link expires**
- **🧩 Multiview tiles follow "Prefer HLS"**
- **⚙️ The first channel after starting the app uses your settings**
- **🎬 Films with subtitles off keep the picture clean**
- **🖼️ "Use this guide's logos" works on the phone**
- **🗓️ Guide days to keep moved next to the other guide settings**
- **💾 A removed card or stick no longer stops downloads**
- **🔎 Settings search finds every setting**
- **📃 Playlists saved with a byte-order mark keep their EPG address** (community PR #7 by @Sekator778)

## v1.0.2 — 2026-09-21

### Fixes

- **💥 The app would not open after updating from v1.0.0**

## v1.0.1 — 2026-09-21

### New features

- **✨ Set a new phone up from the device you already have**
- **⏭️ Next and previous episode, from the player itself**
- **📅 A week of guide, and you choose how much**
- **📺 Protected and MPEG-DASH channels play**

### Fixes

- **📺 Stream info said MPEG-TS on a DASH channel**
- **📺 A channel that will not open can now fall back to the provider's own address**
- **⏺️ Recording a copy-protected channel is refused straight away, and says why**
- **🔗 Pairing with another device during setup no longer does nothing**
- **🗂️ First-run "Restore backup" asks what to restore**
- **📡 The guide keeps downloading when the screen goes off**
- **🎞️ A live channel's frame rate is no longer reported a notch too low**
- **🔎 Search keeps loading results as you scroll, instead of stopping at 40**
- **🎚️ The player's audio and subtitle panels scroll in landscape**
- **🔊 The sound button is always on the player bar**
- **⏸️ No loading spinner over a paused picture**
- **📱 The status bar stays out of full screen**
- **⏩ The fast-forward badge always disappears**
- **▶️ "Resume playback" is a setting again**
- **📶 A channel starts while a portal playlist is still filling itself in**
- **🗂️ Categories your provider lists no longer arrive empty**
- **⏪ Catch-up plays on Stalker portal playlists**
- **⏪ Catch-up loads faster**
- **💬 Catch-up says when a provider has no recording, instead of doing nothing**
- **🔎 Settings search finds every setting, including Multiview and the player engines**
- **📖 The user guide describes first-run setup and guide data accurately**
- **🔗 "Match EPG" lists guide channels again**
- **🔎 The guide picker's search understands names and non-Latin scripts**
- **🔗 Manual EPG matches survive deleting and re-adding a playlist**
- **🤖 Auto-match no longer reports success it cannot deliver**
- **🧹 Duplicate programmes are removed when the guide is downloaded**
- **⚡ The guide keeps less in memory**

## v1.0.0 — 2026-09-14

- **Initial release**

## v0.1.0 — pre-release

### New features

- **📺 The screen stays awake while you watch Live TV**
- **🎛️ The notification, lock screen, small player and floating window control a live channel**
- **⏪ Rewinding a live channel no longer starts a second stream**
- **🔊 Sound only, screen-off picture dropping and mobile-data sound only work on live channels**
- **🪜 A live channel that will not play now tries the other format and the other player**
- **⏱️ "Give up after" now really gives up, instead of a spinner that never ends**
- **⚙️ Live TV player, Live latency, Pre-buffer and Prefer HLS per playlist now reach the player**
- **🔤 Choose the font your subtitles are drawn in**
- **🖼️ The floating window takes the picture's own shape**
- **👆 A small thumb slide no longer closes the player**
- **🕒 The Guide's "On now" keeps meaning now**
- **🚀 The app opens faster from cold**
- **⬆️ The app tells you when there is a new version, and installs it**
- **🔎 Set how big the interface and its text are during setup** (#179)
- **🎨 A real colour picker for the accent, the selection highlight and subtitle text**
- **🏷️ Long-press a category to hide or move it, from Live TV, the Library or the Guide** (#131, thanks @pt5pnzghm6-sys0)
- **♿ A screen reader now announces which row or chip is selected**
- **⏺️ Record Live TV — from the guide, the channel list or while you are watching**
- **🔁 Record every showing of a programme on a channel**
- **⏪ Save a catch-up programme from your provider's archive**
- **🔲 Multiview — several live channels at once**
- **🔊 Sound only: keep a channel's commentary without its picture**
- **🔌 OwnTV works out how many channels your provider allows, and warns you before refusing one**
- **ℹ️ A playlist's Test button is now Info, with Re-test behind it**
- **📂 Save downloads and recordings to a folder you pick**
- **📤 Export a recording or download to a folder of your own**
- **▶️ Live TV on the second player engine, with per-channel compatibility mode**
- **📺 Upcoming programmes show what they are about**
- **📅 Episodes show the day they first aired**
- **🖼️ A profile picture of your own, from your photos**
- **🗓️ Stalker portals: the portal's own TV guide, and catch-up with it**
- **🔎 An empty guide says whether a filter is hiding it, not "add a playlist"**
- **🗺️ A playlist that offers two TV guides sets up both of them**
- **The mobile app builds, signs and installs**
- **Bottom bar on a phone, navigation rail on a tablet**
- **Add your playlist from the phone: Xtream, M3U or MAG portal**
- **Pick an M3U file with the phone's file picker**
- **Restore a backup made on the TV app, encrypted ones included**
- **Local sync: swap your data with your TV over your own Wi-Fi**
- **Send, receive or merge — you pick the direction and what travels**
- **See exactly what a sync will change before it changes anything**
- **A deletion now stays deleted on both devices**
- **The newer of two devices always wins, so a sync never moves you backwards in a show**
- **No password to invent: the two devices lock the transfer themselves, logins included**
- **A device you already paired says so, and skips the PIN**
- **Pairing the same device twice updates it instead of listing it twice**
- **Two phones of the same model are told apart by a short code**
- **Scan the QR code to pair, or find the device on the network**
- **Movies and Series, with categories, sorting and grid or list**
- **Pinch to resize the posters**
- **Press and hold a film or show for its full menu**
- **Film and show details with Resume, seasons and episode progress**
- **A show opens on the season you last watched**
- **Press and hold an episode for its own menu**
- **Film details in a sheet, with the cast and the trailer**
- **Next up card, season progress, watched ticks and a last-watched marker**
- **Hide finished episodes, and sort seasons and episodes oldest or newest first**
- **Home with your own rows, in your order, hiding what you hid on the TV**
- **A hero card for what you were last watching, with Resume**
- **Continue-watching rows for films and shows, with progress**
- **Favourite and recent channels, as logos or as what is on now**
- **Trending titles your playlist actually has**
- **The full trending card: artwork, badges, why it is there, trailer and all versions**
- **Trending layout choice: detailed card or posters only**
- **Who's watching? A profile chooser before the app opens**
- **Locked profiles, kids profiles, and add/rename/delete from Settings**
- **Backup & Restore on the phone, with a password and your own file picker**
- **Choose which profiles and which parts of your data a backup carries**
- **Run a large playlist import in the background**
- **The guide refreshes itself after adding, re-syncing or deleting an EPG source**
- **Fixed: leaving the guide could close the app**
- **Weather on Home, in your own °C or °F**
- **TV guide in three shapes: On now, Grid and Timeline**
- **Day chips, jump to now, search and category filter in the guide**
- **Channels with a dead logo link show a TV symbol instead of a blank square**
- **Tap a programme for its description, catch-up replay or the channel**
- **Size slider for the guide grid**
- **Auto-match EPG, with a review list and a confidence for each match**
- **Sort the guide A–Z, by provider, live, catch-up or favourites**
- **Guide statistics, and a warning when the feed's channel ids match nothing**
- **Add EPG straight from an empty guide**
- **Press and hold a guide channel to match it or shift its times**
- **Live TV: channel list with categories, logos and now/next**
- **Press and hold a channel for its full menu**
- **Watch a channel with the guide, the other channels and catch-up below it**
- **Catch-up TV**
- **Pull down to refresh the playlist**
- **Search the categories instead of scrolling the chip strip**
- **Full screen player with the complete control bar**
- **Touch gestures: skip, scrub, volume, brightness, zoom, speed and mute**
- **Rewind live TV into the archive, and Go live**
- **Mini player above the tabs**
- **A little window you drag around the app, with drag, pinch, double-tap and swipe to close**
- **Choose your mini player: floating window, docked bar or off**
- **Picture-in-Picture on Back, with a window size and edge snapping of your own**
- **Ten seconds back and forward in the Picture-in-Picture window and the notification**
- **A sound-only screen with artwork, volume to 150 % and a sleep timer**
- **Drop the picture from the player, the notification or the little window's menu**
- **Turn the picture off by itself on screen-off or on mobile data, and remember it per channel**
- **Volume boost, picture size, playback speed and stream information**
- **One search field over channels, films and shows**
- **Grouped search results with a count for each kind**
- **Press and hold a search result for its full menu**
- **Recent searches, and shortcuts when the field is empty**
- **Downloads with Active, Completed and Failed**
- **Pause, resume, retry and delete a download**
- **Free space and download speed at a glance**
- **Download to the SD card, with no file permission**
- **Watch a finished download offline**
- **Save a copy of a download anywhere**
- **Settings in ten pages, with a search box**
- **A Playlists page: default, expiry, refresh, test, delete and live import progress**
- **Re-sync a playlist, or re-sync and remove missing titles**
- **An EPG sources page: add, edit, fill from a playlist, refresh schedule and per-feed counts**
- **Use a guide feed's channel logos**
- **Catch-up timezone, and where a catch-up programme plays**
- **Quick toggles at the top of Settings, reorderable by press and hold**
- **Content & metadata and Playback split into pages of their own**
- **Customize opens a folder to show and tidy what is inside it**
- **Rename, hide, reorder and regroup folders and items**
- **Sort a section A–Z or by provider, and filter by hidden**
- **Make your own folders, and choose how new ones arrive**
- **Lock Customize behind a PIN**
- **Press and hold to select a span and act on the whole block**
- **Bulk rename with rules, Auto cleanup, review and Restore originals**
- **Clear the film-details key and server in one press**
- **A notice when the shared film-details allowance runs out**
- **Sign in to OpenSubtitles, with quota and reset time**
- **Restrict subtitle searches to one language**
- **Delete downloaded subtitles: one, all films, all shows, or all**
- **The video player page, in the television's own six sections**
- **Hand Live TV, films or shows to another player app**
- **Show or hide channel numbers**
- **Pin video player switches to Quick, under the television's own names**
- **Auto frame rate asks before turning on where the display cannot be asked**
- **A warning before shrinking the interface past the safe point**
- **Any subtitle colour by hex, and one press back to default**
- **Settings search shows the full path to a result and lands on it**
- **Pickers open as bottom sheets**
- **Live preview while you choose a theme, accent and text size**
- **Frosted glass over your own background picture**
- **New Aurora look**
- **Glass Effect page: six looks, per-part switches, blur, edge light and shadows**
- **Glass with depth: floating, bar, panel and inline panes**
- **Glass reacts to your finger**
- **Menus and pickers are real frosted glass, dragged to half or full height**
- **Blur behind dialogs on Android 12 and newer**
- **Player controls on their own glass panes, with the channel logo**
- **A seek bar made for a thumb, with a time bubble and a buffered band**
- **A live instrument panel: clock, Now and Next, live-edge badge and a programme timeline**
- **Every gesture draws itself, and the phone taps back**
- **OwnTV draws its own icons, matched to the television's**
- **Panes arrive with a travelling light, and a poster grows into its page**
- **Reduce animations now removes every animation in the app, not most of them**
- **The small-window button opens the app's own small player, not the system window**
- **Back from a channel's page stops playback**
- **Sound only is one tap, and shows a moving wave in the small player**
- **Closing the system floating window stops the sound too**
- **Hold a player tool to see its name**
- **Glass Effect switches for the player controls and on-screen messages**
- **A switch for the shine that sweeps across a glass panel**
- **The background picture turns off with the Glass Effect**
- **One press-and-hold menu, the same on Home, library, Live TV and Search**
- **Settings rows grouped on plates, with the app's own switches and sliders**
- **Full screen fills the whole screen, under the notch and behind the bars**
- **Sound only is a slim bar with a moving equaliser, not a page**
- **Full screen always has a picture — sound only lives in the bar**
- **The little window takes the shape of what you are watching**
- **Ten seconds back and forward, or a button to full screen, in the little window**
- **Sideways, the top bar lines up with the tab rail**
- **Fonts page, including the font used in menus and sheets**
- **Switch playlist from the top bar, as on the television**
- **Profiles opens from More**
- **Weather page, with approximate device location**
- **Any accent colour by hex code**
- **Phone-only settings: background playback, Picture-in-Picture, gestures and a data saver**
- **Downloads over Wi-Fi only**
- **Per-playlist player, buffer and pre-buffer overrides**
- **Forget pinned players, saved zoom, volume and audio delay**
- **Test your film-details connection and see the shared allowance**
- **Calls pause playback and hanging up resumes it**
- **Unplugging headphones stops the sound**
- **Lock screen and notification playback controls**
- **Background playback: the sound continues when you leave the app**
- **Favourite, catch-up, sound only and report in the player tool bar**
- **Go back to an earlier time from the player, or pick an exact day and clock time**
- **Search and download subtitles from OpenSubtitles, or pick a file off the phone**
- **Nudge subtitle timing and audio delay, and remember the delay**
- **Next episode counts itself in, with Play now and Cancel**
- **Tune straight to a channel number**
- **Cast to a Chromecast, from the top bar or the player**
- **Casting picks up where the phone was, and gives it back where the television got to**
- **A cast screen with the television's own play, skip, scrub and volume**
- **"Playing on your television" on the notification and the lock screen**
- **A plain message when a Chromecast cannot play the stream**
- **The failure panel names the format, size and decoder**
- **Episode sorting and hiding moved to their own button at the top of a show**
- **Stream information scrolls**
- **Fixed: flicking a long list inside a pop-up sheet closed the app**
- **The Playback page says which settings are television-only**
- **Home settings page: why the trending row is missing, and a button to build it again**
- **Language page with search, each language in its own script and how complete it is**
- **Help translate, with a link, a QR code and a way to ask for a new language**
- **About: version, licence, contributors, source code and the Telegram group with a QR code**
- **Clear history by kind: live, films, shows or all of it**
- **Building the trending row shows its stage and counts**
- **Fixed: Monospace no longer looks identical to System Sans**
- **Fixed: Detailed logging in settings search now lands on the page that has it**
- **Fixed: "Fill from playlist" when adding an EPG source now fills the address in**
- **Fixed: a pop-up you type into rises above the keyboard instead of hiding behind it**
- **Fixed: a full-height sheet no longer runs under the status bar**
- **Picture-in-Picture, with pause and channel +/− buttons**
- **Phone and emulator builds from one signing key, shared with the TV app**
- **26 languages on day one, inherited from the core library**
- **Translation checks in CI** — hardcoded text, number formats, text overflow, packaged languages
- **Tag-driven releases**
- **Automatic core library pin bumps**
- **Two panes on a tablet: Live TV, Movies, Series and Settings side by side**
- **Settings groups open beside the list instead of replacing it**
- **The poster grid counts columns from the space it has, not the screen width**
- **Rotating, unfolding and split screen keep your place**
- **The navigation rail is centred, and scrolls at large display sizes**
- **Downloads and Settings live under More on every screen size**
- **Favourites: everything you starred, on one screen**
- **Watch history, newest first, with your resume points**
- **Clear history sits on the history screen now, not inside Settings**
- **More → Profiles opens the profile manager**
- **Backup & Restore, Local sync, the error log and About moved to More**
- **Settings is down to seven groups and holds settings only**
- **Where downloads are saved, and Wi-Fi-only, moved to the Downloads screen**
- **The first run is the television's own wizard, step for step**
- **Welcome screen with a language picker**
- **Before you start: what OwnTV is and is not**
- **Create your profile as part of setting up**
- **Use a playlist another profile already has**
- **Skip adding a playlist for now**
- **A playlist left unnamed is named for you, as on the television**
- **Fixed: the app closed itself after adding a playlist**
- **Fixed: "Who is watching?" appeared when there was only one profile**
- **Fixed: Home said "Add a playlist first" when a playlist was already there**
- **Fixed: the floating window opened on its own after leaving a channel**
- **Fixed: the Home hero card no longer swallows a tablet screen**
- **Fixed: "Add to Multiview" was written but never shown in the channel menu**
- **Fixed: two channels could play their sound at once in Multiview**
- **Fixed: the channel you tapped kept playing underneath the grid**
- **Fixed: a tile that could not be played still played its sound**
- **Fixed: a menu or picker opened before its data arrived stayed empty until the screen was rotated**
- **Fixed: a picker in landscape showed only its search box**
- **Fixed: the player's channel button could not reach another category or playlist**
- **Fixed: an episode download appeared in no list at all**
- **Fixed: file sizes lost their MB**
- **Fixed: the landscape player HUD had no gap between its two clusters**
