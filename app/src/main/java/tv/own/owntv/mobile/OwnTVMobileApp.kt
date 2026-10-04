package tv.own.owntv.mobile

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.util.Perf
import tv.own.owntv.core.di.coreModule
import tv.own.owntv.core.di.dataModule
import tv.own.owntv.core.di.databaseModule
import tv.own.owntv.core.i18n.AppLocale
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.sync.work.KoinWorkerFactory
import tv.own.owntv.core.util.CrashRecorder
import tv.own.owntv.mobile.di.downloadsModule
import tv.own.owntv.mobile.di.mobileBackupModule
import tv.own.owntv.mobile.di.guideModule
import tv.own.owntv.mobile.di.homeModule
import tv.own.owntv.mobile.di.libraryModule
import tv.own.owntv.mobile.di.liveModule
import tv.own.owntv.mobile.di.playerModule
import tv.own.owntv.mobile.di.searchModule
import tv.own.owntv.mobile.di.settingsModule
import tv.own.owntv.mobile.di.shellModule
import tv.own.owntv.mobile.ui.theme.subtitleFontResource

/**
 * The mobile shell's Application. Deliberately a near-copy of the TV app's `OwnTVApp` for the parts
 * that core depends on — the hooks below are core's only way to learn things a library cannot know
 * about the app compiled around it, and core reads them from its very first line.
 */
class OwnTVMobileApp : Application(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .build()

    /**
     * Wrap the Application base with the selected locale and apply it to the process locale defaults
     * BEFORE the rest of the process starts. The tag is read synchronously from the
     * SharedPreferences-backed store, because DataStore cannot be read here.
     */
    override fun attachBaseContext(base: Context) {
        val tag = LocaleStore.from(base).readBlocking()
        AppLocale.applyGlobally(tag)
        super.attachBaseContext(AppLocale.wrap(base, tag))
    }

    /** Re-apply the selection after the framework's process-level reset on a configuration change. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.applyGlobally(LocaleStore.from(this).readBlocking())
    }

    override fun onCreate() {
        super.onCreate()
        // "Restart now" after an icon change runs a few milliseconds in a process of its own; nothing
        // below may start there (core's AppRestartActivity).
        if (tv.own.owntv.core.brand.AppIconSwitcher.isRestartProcess(this)) return
        // Zero-point for the OwnTVPerf startup timeline (adb logcat -s OwnTVPerf), matching the TV
        // app. Without it every Perf.stamp in core is a silent no-op — which mattered more than a
        // missing timeline: core's database-open stamp is the only thing that proves Room's onOpen
        // callback actually ran under a SQLiteDriver. That callback fails *silently* when it does
        // not (core's plan §5.3), taking the PRAGMAs and the schema self-heal with it, so the phone
        // had no way to detect the one failure mode that ships green.
        Perf.begin()
        // Core has its own BuildConfig and it carries none of this: a library gets no version at all,
        // and the edge key and the maintainer switch are the app's build inputs. Hand them over
        // before the first reader — CrashRecorder, two lines down.
        CoreBuildInfo.versionName = BuildConfig.VERSION_NAME
        CoreBuildInfo.versionCode = BuildConfig.VERSION_CODE
        CoreBuildInfo.edgeKey = BuildConfig.TMDB_EDGE_KEY
        CoreBuildInfo.devTools = BuildConfig.DEV_TOOLS
        CoreBuildInfo.debug = BuildConfig.DEBUG
        CoreBuildInfo.diagnosticBuild = BuildConfig.DIAGNOSTIC_BUILD
        // No LEANBACK_LAUNCHER entry, so a Watch Next row published from here would be a row nothing
        // could open. Without this, core's sync worker tried to write to the TV content provider
        // after every catalog sync — using the two permissions this app's manifest removes.
        CoreBuildInfo.tvHome = false
        // This app's own releases, not the television's. Left at its default, an update check here
        // would offer OwnTV-v*.apk — a different applicationId, which the installer refuses after
        // the whole download.
        CoreBuildInfo.releaseRepo = "ahXN00/OwnTV_Mobile"
        // First thing after the context exists: a crash from here on leaves a trace on disk instead
        // of dying with the process, with the playback ring attached.
        CrashRecorder.diagnostics = { tv.own.owntv.player.LiveDiagnosticsLog.snapshot() }
        CrashRecorder.install(this)
        // Core learns a panel's session limit while syncing; the engine is what acts on it. Registered
        // before Koin so the very first source flow already reaches the player.
        tv.own.owntv.core.player.LiveSessionLimit.report =
            tv.own.owntv.player.LiveStreamQuirks::rememberSessionLimit
        // Which font file the engine hands to libass, so a subtitle is drawn in the face the user
        // chose. This used to be left unset, on the grounds that the app had no fonts of its own —
        // true when the line was written, and untrue since Phase 1 shipped all six families as
        // res/font files. The result was a Subtitle font setting that reached the app's own subtitle
        // layer and nothing else.
        tv.own.owntv.player.SubtitleFontAssets.resourceOf = { it.subtitleFontResource }
        // The eight icon colours are MainActivity + a suffix (see MainActivityColours.kt); core switches them.
        tv.own.owntv.core.brand.AppIconSwitcher.mainActivityClass = MainActivity::class.java.name
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@OwnTVMobileApp)
            modules(
                coreModule, databaseModule, dataModule, playerModule, shellModule,
                liveModule, libraryModule, guideModule, homeModule, searchModule,
                downloadsModule, settingsModule, mobileBackupModule,
            )
        }
        // A chosen icon colour ("Later", the first-run pick, a restore) reaches the launcher when the app
        // is next in the background, never while it is on screen.
        tv.own.owntv.core.brand.AppIconSwitcher.start(this, org.koin.core.context.GlobalContext.get().get())
        // Diagnostics switch, the persisted archive-decode quirk and the one-shot settings migrations.
        tv.own.owntv.player.PlaybackStartup.start(
            context = this,
            scope = appScope,
            settings = org.koin.core.context.GlobalContext.get().get(),
            archiveStore = org.koin.core.context.GlobalContext.get().get(),
        )
        // "Use this guide's logos": never started on the phone before, so the toggle did nothing here.
        // Reads no EPG data at all while no guide source has it on.
        tv.own.owntv.core.epg.EpgLogoStore.start(
            scope = appScope,
            settings = org.koin.core.context.GlobalContext.get().get(),
            epgDao = org.koin.core.context.GlobalContext.get().get(),
        )
    }

    /** Process-long scope for [tv.own.owntv.player.PlaybackStartup]; never cancelled. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** Memory pressure reaches every engine; which levels count is core's call (see PlaybackEngines). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<tv.own.owntv.player.PlaybackEngines>()?.onTrimMemory(level)
        }
    }
}
