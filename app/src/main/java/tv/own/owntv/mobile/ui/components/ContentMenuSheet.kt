package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobileSheetRowsShape

/** The rows stand on a plate a shade darker than the sheet, so the menu reads as one block. */
private const val ROWS_PLATE_ALPHA = 0.26f

/** The line between two groups of actions — a separation, not a rule. */
private const val SHEET_DIVIDER_ALPHA = 0.08f

/**
 * One action in a long-press content menu.
 *
 * [key] is the stable identifier the saved order is written in, and it must match the key core's
 * catalogue uses for the same action — that catalogue is what the settings screen arranges, on
 * either app. [group] only spaces the sheet out: a divider is drawn wherever it changes.
 */
data class SheetAction(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val group: Int = 0,
    val onClick: () -> Unit,
)

/**
 * The long-press menu for a channel, movie, series or episode.
 *
 * The TV app draws these as a focused column in the middle of the screen; here they rise as a
 * bottom sheet, because a phone's actions belong under a thumb. What the two share is the order:
 * both read the same saved arrangement and apply it with core's rule, so a user who moved
 * "Download" to the top of the movie menu on the television finds it at the top here too.
 *
 * Tapping an action dismisses the sheet — every one of them either navigates or changes the row
 * being looked at, and a sheet left open over the result is a sheet the user has to close.
 */
@Composable
fun ContentMenuSheet(
    menu: ContentMenu,
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings: SettingsRepository = koinInject()
    val order by remember(menu) { settings.menuOrder(menu.name.lowercase()) }
        .collectAsStateWithLifecycle(emptyList())

    MobileBottomSheet(onDismissRequest = onDismiss, title = title, modifier = modifier) {
        SheetRows {
            var previousGroup: Int? = null
            applyMenuOrder(actions, order) { it.key }.forEach { action ->
                if (previousGroup != null && action.group != previousGroup) SheetDivider()
                previousGroup = action.group
                SheetActionRow(action = action, onDismiss = onDismiss)
            }
        }
    }
}

/** The plate a sheet's rows sit on, a shade darker than the sheet itself. */
@Composable
private fun SheetRows(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileSheetRowsShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = ROWS_PLATE_ALPHA))
            .padding(vertical = 2.dp),
        content = content,
    )
}

@Composable
private fun SheetDivider() {
    Spacer(
        Modifier
            .padding(
                horizontal = MobileDimens.SheetRowPaddingH,
                vertical = MobileDimens.GapTiny,
            )
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = SHEET_DIVIDER_ALPHA)),
    )
}

@Composable
private fun SheetActionRow(action: SheetAction, onDismiss: () -> Unit) {
    val color =
        if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { action.onClick(); onDismiss() }
            .padding(
                horizontal = MobileDimens.SheetRowPaddingH,
                vertical = MobileDimens.SheetRowPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(MobileDimens.SheetRowIconSize), contentAlignment = Alignment.Center) {
            if (action.icon != null) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    // The glyph is a marker, not the message: quiet unless the action is destructive.
                    tint = if (action.destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(MobileDimens.ListRowIconGap))
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
