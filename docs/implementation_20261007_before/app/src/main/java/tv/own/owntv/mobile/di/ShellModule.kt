package tv.own.owntv.mobile.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.mobile.ui.profiles.ProfileGateSession
import tv.own.owntv.mobile.ui.profiles.ProfilesViewModel
import tv.own.owntv.mobile.ui.screens.ContentActions
import tv.own.owntv.mobile.ui.screens.settings.BackupViewModel
import tv.own.owntv.mobile.ui.setup.SetupViewModel
import tv.own.owntv.mobile.ui.shell.ShellViewModel
import tv.own.owntv.mobile.ui.shell.StartupLiveSelection

/** The app shell's own bindings. `NavVisibility` and `SourceImporter` come from core's `dataModule`. */
val shellModule = module {
    // Shared by every screen with a long-press menu, so it is bound where nothing owns it.
    singleOf(::ContentActions)
    // Set by the shell on launch, taken by Live TV when it opens — neither owns the other.
    singleOf(::StartupLiveSelection)
    viewModelOf(::ShellViewModel)
    viewModel { ProfilesViewModel(get(), get(), get(), androidContext()) }
    viewModelOf(::ProfileGateSession)
    // The counts the More rows and the Favourites / History chips carry.
    viewModel {
        tv.own.owntv.mobile.ui.screens.MoreCountsViewModel(get(), get(), get(), get(), get())
    }
    viewModel { BackupViewModel(get(), get(), androidContext(), get(), get()) }
    viewModel { tv.own.owntv.mobile.ui.screens.settings.LocalSyncViewModel(get()) }
    // An import has to outlive the wizard screen that started it — "Run in background" is exactly
    // that promise — so it runs here rather than in a ViewModel that dies with its navigation entry.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    viewModel { SetupViewModel(get(), get(), get(), get(), androidContext()) }
}
