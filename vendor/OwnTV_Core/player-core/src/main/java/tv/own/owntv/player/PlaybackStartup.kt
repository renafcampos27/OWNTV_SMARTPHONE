package tv.own.owntv.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.player.ArchiveDecodeStore
import tv.own.owntv.core.settings.SettingsRepository

/** Initialize the customized settings and diagnostic policy before the first engine is created. */
object PlaybackStartup {
    fun start(context: Context, scope: CoroutineScope, settings: SettingsRepository, archiveStore: ArchiveDecodeStore) {
        LiveDiagnosticsLog.init(context)
        PlaybackSettings.of(settings)
        scope.launch { settings.detailedDiagnostics.collect { on -> LiveDiagnosticsLog.enabled = on || CoreBuildInfo.debug || CoreBuildInfo.diagnosticBuild } }
        scope.launch {
            val known = runCatching { archiveStore.hosts() }.getOrDefault(emptySet())
            LiveStreamQuirks.installArchivePersistence(known) { host -> scope.launch { archiveStore.remember(host) } }
        }
    }
}
