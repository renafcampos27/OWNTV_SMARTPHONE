package tv.own.owntv.mobile.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.mobile.ui.screens.guide.GuideViewModel

/**
 * The Guide's view model, and the core reader it asks.
 *
 * [GuideReader] is a plain core class with no module of its own — like the Live helpers, it is
 * assembled here. A `single` because it holds nothing: what to keep is the screen's decision.
 */
val guideModule = module {
    single { GuideReader(get(), get(), get(), get()) }
    viewModelOf(::GuideViewModel)
}
