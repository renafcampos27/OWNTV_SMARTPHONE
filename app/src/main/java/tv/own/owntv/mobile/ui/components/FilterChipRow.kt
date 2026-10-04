package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassClickable
import tv.own.owntv.mobile.ui.theme.glassSurface

/** A chip is a pill, at both settings. */
private val ChipShape = RoundedCornerShape(percent = 50)

/**
 * The horizontal filter strip above a list — categories on Live, genres in the library.
 *
 * Selection is explicit and momentary: there is no "currently focused" chip to keep track of,
 * only the one the user chose. The chosen chip wears the accent on its rim and holds it; a chip
 * being pressed sinks under the finger and ticks. Neither draws a ripple, which over glass reads
 * as a smear rather than as a touch.
 */
@Composable
fun FilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** What a long press on a chip offers, where there is anything to offer — hide and move, on a
     *  category strip. A strip with nothing behind a chip leaves this null. */
    onLongPress: ((Int) -> Unit)? = null,
    onLongPressLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                start = MobileDimens.ChipRowPaddingH,
                end = MobileDimens.ChipRowPaddingH,
                top = MobileDimens.ChipRowPaddingTop,
                bottom = MobileDimens.ChipRowPaddingBottom,
            ),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.ChipGap),
    ) {
        labels.forEachIndexed { index, label ->
            val chosen = index == selectedIndex
            val press = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = MobileDimens.ChipHeight)
                    .glassSurface(
                        surface = GlassSurface.CARDS,
                        shape = ChipShape,
                        interactionSource = press,
                        selected = chosen,
                    )
                    .clip(ChipShape)
                    // The chosen chip is told apart by its accent alone, which says nothing to a
                    // screen reader. TalkBack announces this one as "selected".
                    .semantics { selected = chosen }
                    .glassClickable(
                        press,
                        onClick = { onSelect(index) },
                        onLongClick = onLongPress?.let { { it(index) } },
                        onLongClickLabel = onLongPressLabel,
                    )
                    .padding(horizontal = MobileDimens.ChipPaddingH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
