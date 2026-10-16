package tv.own.owntv.core.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.settings.SettingsRepository.SortMode

class LiveSortRoutingTest {
    private fun route(key: LiveKey, mode: SortMode, saved: Boolean): String {
        var called = ""
        val page = object: PagingSource<Int,ChannelEntity>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int,ChannelEntity> = LoadResult.Page(emptyList(),null,null)
            override fun getRefreshKey(state: PagingState<Int,ChannelEntity>): Int? = null
        }
        fun <T : Any> dao(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _, method, _ ->
            called=method.name;page
        }))
        livePagingSource(key,7,listOf(1),"",mode,dao(ChannelDao::class.java),dao(CustomCategoryDao::class.java),{"folder"},{saved})
        return called
    }
    @Test fun alphabeticalFolderIgnoresManualRows() {
        assertEquals("pagingByCategoryAlpha",route(LiveKey.Folder(10),SortMode.ALPHA,true))
    }
    @Test fun providerFolderIgnoresManualRows() {
        assertEquals("pagingByCategory",route(LiveKey.Folder(10),SortMode.PLAYLIST,true))
    }
    @Test fun customFolderUsesSavedRows() {
        assertEquals("pagingByCategoryManual",route(LiveKey.Folder(10),SortMode.CUSTOM,true))
    }
    @Test fun customAllUsesSavedRowsAndHasCheapEmptyFallback() {
        assertEquals("pagingAllManual",route(LiveKey.All,SortMode.CUSTOM,true))
        assertEquals("pagingAllOriginal",route(LiveKey.All,SortMode.CUSTOM,false))
    }
    @Test fun catchupUsesItsOwnCustomOrder() {
        assertEquals("pagingAllManual",route(LiveKey.Catchup,SortMode.CUSTOM,true))
        assertEquals("pagingCatchup",route(LiveKey.Catchup,SortMode.ALPHA,true))
    }
    @Test fun customCategorySeparatesNaturalAndManualOrdering() {
        assertEquals("pagingChannelsNatural",route(LiveKey.Custom("custom"),SortMode.ALPHA,true))
        assertEquals("pagingChannelsNatural",route(LiveKey.Custom("custom"),SortMode.PLAYLIST,true))
        assertEquals("pagingChannels",route(LiveKey.Custom("custom"),SortMode.CUSTOM,true))
    }
}
