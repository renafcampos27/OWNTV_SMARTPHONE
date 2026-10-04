package tv.own.owntv.mobile.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.core.subtitles.SubtitleController
import tv.own.owntv.mobile.cast.CastController
import tv.own.owntv.mobile.playback.DataSaverGate
import tv.own.owntv.mobile.playback.PipController
import tv.own.owntv.mobile.ui.player.SubtitleSearchViewModel
import tv.own.owntv.mobile.ui.screens.library.VodTuner
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaybackSession
import tv.own.owntv.player.PlayerDiagnostics
import tv.own.owntv.player.ScreenOff
import tv.own.owntv.player.SleepTimer
import tv.own.owntv.player.livePreviewEngine
import tv.own.owntv.player.ownTVPlayer

/**
 * The libmpv player, bound app-side.
 *
 * `:player-core` deliberately ships no Koin module of its own — it is a plain library, and the app
 * that hosts it decides how many engines it wants and when they are created. The TV app's
 * `playerModule` binds five (full player, live preview, hero preview, session, subtitles); this one
 * binds the two that a mobile app cannot do without, and will grow as the real screens land.
 *
 * Every constructor argument resolves out of core's own `dataModule`, so nothing else has to be
 * declared here. The two engines are built by player-core's shared builders (`ownTVPlayer()`,
 * `livePreviewEngine()`), the same ones the television uses.
 */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engine can't expose.
    single { PlayerDiagnostics() }
    // Multiview's engines, one per tile. Live playback here is mpv, which is a single fullscreen
    // engine by design; four pictures at once is what the ExoPlayer live engine exists for, and the
    // pool is the only thing that ever builds more than one of it.
    single { tv.own.owntv.player.LiveEnginePool { livePreviewEngine() } }
    // L2 - live's own ExoPlayer engine, the second engine the phone's HUD can now swap to. It is
    // DELIBERATELY not borrowed from the pool above: leaving Multiview calls releaseAll(), which
    // would take the channel the user is watching down with the tiles. One instance, session-long,
    // the same arrangement the television has.
    single { livePreviewEngine() }
    // Every engine, so memory pressure reaches the ExoPlayer ones and the Multiview tiles too. The
    // phone uses only its onTrimMemory: its background policy (sound on with the screen off, PiP) is
    // its own, and the television's stop-everything-on-Home is exactly what it must not do.
    single {
        tv.own.owntv.player.PlaybackEngines(
            player = get(),
            livePreview = get(),
            pool = get(),
        )
    }
    // Shared between the activity (which enters PiP) and the player screen (which knows whether the
    // picture is on screen at all).
    single { PipController() }
    // Asked by both tuners before a stream opens, so Data saver refuses in one place rather than two.
    single { DataSaverGate(context = androidContext(), settings = get(), localeStore = get()) }
    // Audio focus and the system media session. Both arguments differ from the television's defaults
    // for the same reason: this is a phone. A call PAUSES the film rather than playing it quietly
    // under the caller, and unplugging headphones stops it instead of switching to the loudspeaker.
    single {
        PlaybackSession(
            context = androidContext(),
            focusPolicy = PlaybackSession.FocusPolicy.PAUSE,
            pauseWhenOutputDisconnects = true,
        )
    }
    // Casting. Built with the session, because a cast session takes the lock screen and the audio
    // focus exactly as the local engine does — it is another PlaybackEngine, and nothing else about
    // the media session had to learn what a Chromecast is.
    single { CastController(context = androidContext(), session = get()) }
    single { ownTVPlayer() }
    // Bridges the playing item to the OpenSubtitles search, and owns the downloaded-subtitle cache
    // the settings page deletes from. Bound here rather than with the rest of the subtitle stack
    // because it takes the player, which is this module's.
    single { SubtitleController(get(), get(), get(), get()) }
    // Backs the player's "Add subtitles" sheet.
    viewModelOf(::SubtitleSearchViewModel)
    // The sleep timer stops whichever tuner is playing. Both are resolved when it fires rather than
    // when it is built, so a timer nobody set never creates them. It ends itself once the session
    // detaches (nothing playing), so a stop by hand no longer leaves it counting.
    single { ScreenOff(androidContext()) }
    single {
        SleepTimer(active = get<PlaybackSession>().active, screenOff = get()).apply {
            stopPlayback = {
                if (get<LiveTuner>().channel.value != null) get<LiveTuner>().stop() else get<VodTuner>().stop()
            }
        }
    }
}
