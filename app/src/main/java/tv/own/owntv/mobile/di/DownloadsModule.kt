package tv.own.owntv.mobile.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import tv.own.owntv.mobile.ui.screens.downloads.DownloadsViewModel
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsViewModel

/**
 * The Downloads and Recordings screens. Both queues are core's and already registered there — these
 * are only the view models. Downloads needs the context for the volume list and the system save
 * dialog; Recordings needs nothing but core and the tuner that plays a file.
 */
val downloadsModule = module {
    viewModel { RecordingsViewModel(context = androidContext(), settings = get(), recordings = get(), vodTuner = get()) }
    viewModel {
        DownloadsViewModel(
            context = androidContext(),
            downloadDao = get(),
            movieDao = get(),
            seriesDao = get(),
            categoryDao = get(),
            profileDao = get(),
            customize = get(),
            settings = get(),
            downloadManager = get(),
            vodTuner = get(),
        )
    }
}
