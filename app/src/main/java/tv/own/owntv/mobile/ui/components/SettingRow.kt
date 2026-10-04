package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A value never grows past this: the title has first claim on the row. */
private val VALUE_MAX_WIDTH = 118.dp

/** The chevron is a marker, not a button — small, quiet, and close to the value it follows. */
private val CHEVRON_SIZE = 15.dp
private val CHEVRON_GAP = 6.dp

/**
 * One line of a settings screen, in the three shapes the TV app's settings use: a switch, a
 * current value, or a chevron into a sub-screen. Built on [MobileListRow] so a settings row is
 * the same height and the same long-press target as every other list row in the app.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    MobileListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = {
            if (!enabled) return@MobileListRow
            // Tapping anywhere on a switch row toggles it; the switch itself is not a target.
            if (checked != null && onCheckedChange != null) onCheckedChange(!checked) else onClick()
        },
        leading = leading,
        trailing = when {
            checked != null -> {
                {
                    // No callback: the whole row is the button, so the switch only shows state.
                    MobileSwitch(checked = checked, enabled = enabled)
                }
            }
            value != null || showChevron -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value != null) {
                            Text(
                                text = value,
                                // Right-aligned and capped: a long value ellipsises rather than
                                // pushing the title out of the row.
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
                            )
                        }
                        if (showChevron) {
                            Icon(
                                imageVector = MobileIcons.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(start = if (value != null) CHEVRON_GAP else 0.dp)
                                    .size(CHEVRON_SIZE),
                            )
                        }
                    }
                }
            }
            else -> null
        },
    )
}
