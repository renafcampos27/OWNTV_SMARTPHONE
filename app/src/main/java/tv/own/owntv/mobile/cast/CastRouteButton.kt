package tv.own.owntv.mobile.cast

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import tv.own.owntv.mobile.R

/**
 * The cast button — the platform's own, deliberately.
 *
 * It is one of the few controls Android users recognise by shape, and the Cast SDK drives it: it
 * appears only when a receiver is actually reachable, it animates while connecting, and tapping it
 * opens the system device chooser. Drawing a Material 3 icon and reimplementing the chooser would
 * cost more code and be less familiar.
 *
 * Two things it needs that this app does not otherwise have:
 * - **an AppCompat theme, on the activity itself.** The wrapper below dresses the button, but the
 *   chooser is built by a DialogFragment against the ACTIVITY's theme, which the wrapper never
 *   reaches. That is why `Theme.OwnTVMobile` is parented to AppCompat — under the old framework
 *   Material parent the first tap threw `background can not be translucent: #0`, MediaRouter having
 *   read a `colorPrimary` that did not exist as transparent.
 * - **a `FragmentActivity`**, because the chooser is a fragment. That is why `MainActivity` extends
 *   one — see its class doc.
 *
 * [light] picks the tint. The player draws it over video, which is always dark; the top bar follows
 * whichever way round the app's own theme is, which is not necessarily the system's.
 */
@Composable
fun CastRouteButton(light: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.common_cast)
    AndroidView(
        modifier = modifier
            .size(48.dp)
            // The View has no accessible label of its own, and "Cast" is already translated in core.
            .semantics { contentDescription = label },
        factory = { context ->
            val themed = ContextThemeWrapper(
                context,
                if (light) R.style.Theme_OwnTVMobile_CastButton else R.style.Theme_OwnTVMobile_CastButton_Dark,
            )
            MediaRouteButton(themed).also { button ->
                // Throws where Google Play services is missing, which is a phone that cannot cast at
                // all: the button then simply never shows itself.
                runCatching { CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button) }
            }
        },
    )
}
