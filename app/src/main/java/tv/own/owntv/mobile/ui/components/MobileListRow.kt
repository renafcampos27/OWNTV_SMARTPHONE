package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.compositeOver
import tv.own.owntv.mobile.ui.stage.stageSelectedEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.own.owntv.mobile.ui.theme.MobileChipShape
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassClickable

/**
 * One tappable line in a list — a channel, a profile, a download.
 *
 * Long-press is how a touch user reaches the actions the TV app puts behind a context menu, so it
 * is part of the row rather than something each screen adds. A row without [onLongClick] simply
 * has no menu.
 */
@Composable
fun MobileListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    /**
     * Whether this row is the chosen one in its list.
     *
     * A tick drawn in [trailing] says so to someone looking at it and to nobody else: the glyph is
     * decorative, so a screen reader reads the title and stops. This puts the same fact in the
     * semantics, where TalkBack announces it as "selected".
     */
    selected: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val focused by press.collectIsFocusedAsState()
    val accent = MaterialTheme.colorScheme.primary
    val shape = remember { RoundedCornerShape(16.dp) }
    val background = when {
        selected -> accent.copy(alpha = .13f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)
        focused -> accent.copy(alpha = .08f)
        pressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MobileDimens.ListRowHeight)
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(1.dp, accent.copy(alpha = .55f), shape).stageSelectedEdge(accent) else if (focused) Modifier.border(1.dp, accent, shape) else Modifier)
            .semantics { this.selected = selected }
            .glassClickable(press, onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = MobileDimens.ListRowPaddingH,
                vertical = MobileDimens.ListRowPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            RowIconChip(leading)
            Spacer(Modifier.width(MobileDimens.ListRowIconGap))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.14).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    // Smaller and quieter than the title, and only just below it: a subtitle that
                    // sits a whole line away stops reading as part of the same row.
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(start = MobileDimens.ListRowIconGap)) { trailing() }
        }
    }
}

/**
 * A row's icon, on its own tinted plate rather than loose against the text.
 *
 * The plate is what gives a settings list its rhythm: every title starts at the same place whether
 * the row has an icon or not, and the icon reads as a marker for the row rather than a picture in it.
 */
@Composable
private fun RowIconChip(icon: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(MobileDimens.ListRowIconSize)
            .clip(MobileChipShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MobileChipShape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            // A box narrower than an icon's own default is what sizes it: the caller passes a plain
            // Icon and it comes out at the plate's scale rather than Material's 24 dp.
            Box(Modifier.size(MobileDimens.ListRowIconGlyph), content = { icon() })
        }
    }
}
