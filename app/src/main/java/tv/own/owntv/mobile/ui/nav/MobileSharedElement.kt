package tv.own.owntv.mobile.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion

/**
 * The two scopes a shared element needs, carried as composition locals.
 *
 * They could be parameters, but they would be parameters on every screen between the navigation graph
 * and the one tile that uses them — six signatures widened so that two of them can be read. Locals are
 * what a cross-cutting scope is for.
 *
 * Both are null wherever no transition is running (a preview, the theme gallery, a bottom sheet in its
 * own window), and [sharedPoster] then does nothing at all.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Mark this artwork as the same picture as the one under [key] on the screen being navigated to.
 *
 * The poster the user tapped is the poster that grows into the detail header, rather than one tile
 * fading out while an unrelated picture fades in somewhere else. It travels on the app's own spatial
 * spring, so it stops dead when animations are off like everything else does.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(key: String): Modifier {
    val transition = LocalSharedTransition.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    val motion = LocalMobileMotion.current
    return with(transition) {
        this@sharedPoster.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> motion.spatial() },
        )
    }
}

/** The key both ends of the journey agree on: one item, one picture. */
fun posterKey(tab: String, itemId: Long): String = "poster:$tab:$itemId"
