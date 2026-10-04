package tv.own.owntv.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion
import tv.own.owntv.mobile.ui.theme.MobileChipShape
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassClickable

/** How faint the divider between two rows is. Present at a glance, invisible when read. */
private const val DIVIDER_ALPHA = 0.35f

/** How much the row darkens under a finger — a list flashes, it does not shrink. */
private const val PRESS_ALPHA = 0.08f

/** The icon plate: a wash of the accent, with the same hairline every glass edge carries. */
private const val CHIP_FILL_ALPHA = 0.15f
private const val CHIP_RIM_ALPHA = 0.07f

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
    // The hairline that divides one row from the next, inset to where the title starts rather than
    // ruled across the whole width: an inset line separates rows, a full-width one cuts the page up.
    val dividerInset = MobileDimens.ListRowPaddingH +
        if (leading != null) MobileDimens.ListRowIconSize + MobileDimens.ListRowIconGap else 0.dp
    val dividerColour = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)
    // A row has no pane of its own: it is a line on the page it sits on, separated from its
    // neighbours by the hairline. Giving each row its own glass makes a list read as a stack of
    // loose slabs, which is the one thing a settings list must not look like.
    val pressed by press.collectIsPressedAsState()
    val pressTint by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = PRESS_ALPHA)
        } else {
            Color.Transparent
        },
        animationSpec = LocalMobileMotion.current.fast(),
        label = "rowPress",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MobileDimens.ListRowHeight)
            .background(pressTint)
            .drawWithContent {
                drawContent()
                // The inset follows the title, so in Arabic it starts from the right edge — a
                // hairline that always began on the left would run under the icon and stop short of
                // the text it is meant to underline.
                val inset = dividerInset.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                drawLine(
                    color = dividerColour,
                    start = Offset(if (rtl) 0f else inset, 0f),
                    end = Offset(if (rtl) size.width - inset else size.width, 0f),
                    strokeWidth = 1f,
                )
            }
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
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.14).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    // Smaller and quieter than the title, and only just below it: a subtitle that
                    // sits a whole line away stops reading as part of the same row.
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
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
            .background(accent.copy(alpha = CHIP_FILL_ALPHA))
            .border(1.dp, accent.copy(alpha = CHIP_RIM_ALPHA), MobileChipShape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            // A box narrower than an icon's own default is what sizes it: the caller passes a plain
            // Icon and it comes out at the plate's scale rather than Material's 24 dp.
            Box(Modifier.size(MobileDimens.ListRowIconGlyph), content = { icon() })
        }
    }
}
