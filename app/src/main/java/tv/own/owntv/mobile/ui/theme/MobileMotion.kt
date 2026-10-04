package tv.own.owntv.mobile.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import tv.own.owntv.core.theme.AnimationLevel

/**
 * How things move in this app.
 *
 * Two families, and the distinction is the whole point:
 *
 * - **Spatial** — anything that changes position or size. Springs, with a little bounce, because a
 *   pane that overshoots by a hair and settles reads as a physical object arriving. This is a touch
 *   device: the user's finger threw it, and it should behave as though it has mass.
 * - **Effects** — anything that changes colour, alpha or elevation. No bounce, ever. A colour that
 *   overshoots is a flicker, not an object.
 *
 * With animations off every spec is [snap], so nothing is merely *fast*: it is absent. That is what
 * the setting promises, and it means a caller reaching for one of these never has to check it.
 *
 * **Not** a Material `MotionScheme`. That interface and `MaterialExpressiveTheme` are both `internal`
 * in material3 1.4.0, which is the version this app resolves — the plan's pre-check said otherwise and
 * has been corrected. Material's own components therefore keep Material's own motion until the day
 * that interface opens up; this scheme is what *this app's* animations use.
 */
class MobileMotion(level: AnimationLevel) {

    private val off = level == AnimationLevel.OFF

    /** A pane arriving, a sheet settling, an element travelling from one screen to the next. */
    fun <T> spatial(): FiniteAnimationSpec<T> = if (off) snap() else spring(0.85f, 380f)

    /** Something small and quick, and never bouncy: a press, a chip, a colour. */
    fun <T> fast(): FiniteAnimationSpec<T> =
        if (off) snap() else spring(Spring.DampingRatioNoBouncy, 900f)
}

/** The app's motion, read once at the theme rather than by every control that animates. */
val LocalMobileMotion = staticCompositionLocalOf { MobileMotion(AnimationLevel.FULL) }
