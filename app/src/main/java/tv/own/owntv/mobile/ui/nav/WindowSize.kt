package tv.own.owntv.mobile.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Whether there is room to show a list and what it opens side by side.
 *
 * 840dp is Material's medium/expanded boundary — the width at which a list pane and a detail pane
 * both stay usable rather than becoming two cramped columns. The rail already arrives at 600dp,
 * which is the compact/medium boundary and a separate decision.
 *
 * `screenWidthDp` is the *window*, not the display: in split screen and on a folded device it is
 * already the smaller number, which is what makes step 7 of the phase work without a device check.
 */
@Composable
@ReadOnlyComposable
fun isExpandedWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= EXPANDED_DP

private const val EXPANDED_DP = 840
