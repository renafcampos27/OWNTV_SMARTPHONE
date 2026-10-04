package tv.own.owntv.mobile.ui.components

import tv.own.owntv.mobile.ui.nav.sharedPoster
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.own.owntv.core.theme.GlassSurface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.ui.theme.MobileCardShape
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobilePosterShape
import tv.own.owntv.mobile.ui.theme.glassClickable
import tv.own.owntv.mobile.ui.theme.glassSurface

/**
 * A poster tile for a movie, a series or an episode. 100 dp wide fits three columns on a 360 dp
 * phone; the caller widens it in landscape and on a tablet.
 *
 * [progress] draws the resume bar along the bottom of the artwork, in 0..1. Null hides it.
 */
@Composable
fun PosterCard(
    title: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    width: Dp = MobileDimens.PosterWidthPortrait,
    // Set where tapping the tile opens a screen that shows the same picture — see [sharedPoster].
    sharedKey: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    // The card draws its own press: it sinks and its rim lights, where a ripple would spread a
    // smear across the frost.
    val press = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width)
            .glassSurface(GlassSurface.CARDS, MobileCardShape, interactionSource = press)
            .clip(MobileCardShape)
            .glassClickable(press, onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = MobileDimens.PosterPadding,
                end = MobileDimens.PosterPadding,
                top = MobileDimens.PosterPadding,
                bottom = MobileDimens.PosterPaddingBottom,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (sharedKey != null) Modifier.sharedPoster(sharedKey) else Modifier)
                .clip(MobilePosterShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            // The title stands in for the artwork, and stays underneath it: it is what an M3U
            // playlist with no poster URLs shows for most of its catalogue, and it is also what is
            // left when a URL that exists fails to load — otherwise that tile is an empty grey box.
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(MobileDimens.GapSmall),
            )
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(MobileDimens.PosterProgressHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            // One line: a wrapped title makes every tile in the row a different height, and the
            // second line is what runs into the neighbouring column.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = MobileDimens.PosterTextInset,
                end = MobileDimens.PosterTextInset,
                top = MobileDimens.GapTiny + 2.dp,
            ),
        )
        // Drawn even when there is nothing to say, for the reason the title is held to one line: half
        // a catalogue has a rating and half has none, and a tile that skips the line is shorter than
        // the four beside it, so the row's cards end at four different heights.
        Text(
            text = subtitle.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MobileDimens.PosterTextInset),
        )
    }
}
