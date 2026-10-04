package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.ui.screens.settings.customize.CustomizeCategoriesPane
import tv.own.owntv.mobile.ui.screens.settings.customize.CustomizeItemsPane
import tv.own.owntv.mobile.ui.screens.settings.customize.CustomizeItemsViewModel
import tv.own.owntv.mobile.ui.screens.settings.customize.CustomizePinGate
import tv.own.owntv.mobile.ui.screens.settings.customize.CustomizeViewModel

/**
 * Hide, rename, reorder and regroup everything the playlists brought in — folders on the first
 * level, the channels or films inside one of them on the second.
 *
 * Both levels live in this one page rather than in two navigation routes, because a folder is
 * identified by a key that carries the provider's own name and would not survive being written into
 * a route string. Back therefore steps from the items list to the folder list first.
 */
@Composable
fun SettingsCustomizePage(
    modifier: Modifier = Modifier,
    vm: CustomizeViewModel = koinViewModel(),
    itemsVm: CustomizeItemsViewModel = koinViewModel(),
) {
    val pinLock by vm.pinLock.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    // Unlocking lasts as long as the page is open; leaving settings asks for the PIN again.
    var unlocked by remember { mutableStateOf(false) }

    // Feed the second level from the first, so it always pages the folder that was just opened.
    val ctx = vm.ctxForItems()
    LaunchedEffect(selected, ctx) {
        val row = selected
        if (row != null && ctx != null) {
            itemsVm.open(
                CustomizeItemsViewModel.CatInfo(
                    categoryId = ctx.categoryId,
                    contextKey = row.key,
                    mediaType = ctx.mediaType,
                    sourceIds = ctx.sourceIds,
                ),
            )
        } else {
            itemsVm.close()
        }
    }

    // Nothing is shown until the stored PIN is known, so a locked page never flashes its contents.
    if (!pinLock.loaded) return

    val pin = pinLock.pin
    if (pin != null && !unlocked) {
        CustomizePinGate(storedPin = pin, onUnlock = { unlocked = true })
        return
    }

    if (selected != null) {
        BackHandler { vm.closeItems() }
        CustomizeItemsPane(
            vm = itemsVm,
            categoryName = selected!!.displayName,
            onBack = { vm.closeItems() },
            modifier = modifier,
        )
    } else {
        CustomizeCategoriesPane(vm = vm, onOpenItems = vm::openItems, modifier = modifier)
    }
}
