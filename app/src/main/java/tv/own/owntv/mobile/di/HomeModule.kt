package tv.own.owntv.mobile.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.mobile.ui.screens.home.HomeViewModel

/**
 * Home's rails come out of core, the same reader the television's Home uses, so the two apps agree
 * item for item on what is on it. [guideModule] provides the guide reader it takes.
 */
val homeModule = module {
    single {
        HomeFeedReader(
            planner = get(),
            movieDao = get(),
            seriesDao = get(),
            channelDao = get(),
            categoryDao = get(),
            customize = get(),
            sourceDao = get(),
            settings = get(),
            profileDao = get(),
            trendingDao = get(),
            guide = get(),
        )
    }
    viewModelOf(::HomeViewModel)
}
