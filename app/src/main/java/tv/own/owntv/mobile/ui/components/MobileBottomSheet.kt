package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** A sheet's list may take at most half the screen: the buttons under it have to stay reachable. */
@Composable
fun sheetListHeight(): Dp {
    val config = LocalConfiguration.current
    // Half the screen is right in portrait, where the sheet shares the screen with what is behind it.
    // In landscape a phone is only ~400dp tall, so half of it was barely one row — a picker that
    // showed its search field and nothing else. Landscape gets most of the height, because in
    // landscape there is nothing useful to see behind the sheet anyway.
    val fraction = if (config.screenHeightDp < config.screenWidthDp) 0.78f else 0.5f
    return (config.screenHeightDp * fraction).dp
}

/**
 * A sheet body that is allowed to be taller than the sheet it is in.
 *
 * [MobileBottomSheet] hands its content to a plain column with no ceiling, and a column that runs out
 * of room gives its *last* children no height at all — so whatever sits below a list (a search row, a
 * timing nudge, the sixth aspect ratio) silently vanished rather than pushing the sheet taller. The
 * player is where it bit, because the player is always landscape and a phone on its side is barely
 * 400 dp tall.
 *
 * The cap is [sheetListHeight], the one the rest of the app already uses, and the host's nested
 * scroll hands the drag back to the sheet once this has scrolled to its own top.
 */
@Composable
fun SheetScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .heightIn(max = sheetListHeight())
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.SheetGap),
        content = content,
    )
}

/**
 * The bottom sheet every long-press menu and picker in this app uses.
 *
 * It replaces the TV app's centred popups: a sheet rises into the bottom third of the screen, where
 * a thumb already is, and the system back gesture or a tap outside dismisses it.
 *
 * Calling this does not draw anything here — it hands the sheet to [MobileSheetHost], which draws it
 * at the top of the app's own window. That is what lets it frost the same wallpaper the rest of the
 * app frosts; a separate dialog window cannot.
 */
@Composable
fun MobileBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = checkNotNull(LocalSheetHost.current) {
        "MobileBottomSheet needs a MobileSheetHost above it"
    }
    val entry = remember { SheetEntry(onDismissRequest, modifier, title, content) }
    // The caller recomposes with new lambdas and a new title on every frame it feels like; the entry
    // is the same open sheet throughout, so it is updated in place rather than replaced.
    SideEffect {
        entry.onDismissRequest = onDismissRequest
        entry.modifier = modifier
        entry.title = title
        entry.content = content
    }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}
