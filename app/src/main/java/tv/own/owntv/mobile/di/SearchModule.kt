package tv.own.owntv.mobile.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.mobile.ui.screens.search.SearchViewModel

/**
 * Search, and the core reader that does it.
 *
 * [SearchReader] uses the private Core and shares profile/source filtering with the Guide.
 * Programme search is integrated here without changing the television checkout.
 */
val searchModule = module {
    single { SearchReader(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::SearchViewModel)
}
