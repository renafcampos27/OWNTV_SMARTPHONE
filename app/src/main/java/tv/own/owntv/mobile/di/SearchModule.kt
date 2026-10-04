package tv.own.owntv.mobile.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.mobile.ui.screens.search.SearchViewModel

/**
 * Search, and the core reader that does it.
 *
 * [SearchReader] is the television's search engine word for word: assembled here the same way the
 * Guide's reader is, so the two apps cannot drift into finding different things for the same term.
 */
val searchModule = module {
    single { SearchReader(get(), get(), get(), get(), get(), get()) }
    viewModelOf(::SearchViewModel)
}
