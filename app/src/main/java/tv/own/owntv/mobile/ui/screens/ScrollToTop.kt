package tv.own.owntv.mobile.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.SharedFlow
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.ui.theme.LocalAnimations

/**
 * Jumps this list to the top when the shell says its tab was tapped again or long-pressed.
 *
 * It lived in `PlaceholderScreen.kt` while that file existed — the placeholder was a long list
 * precisely so this could be tested before any real tab scrolled. Every tab is real now and the
 * placeholder is gone, so the one piece of it that was never scaffolding has a file of its own.
 *
 * With animations off it jumps rather than animates: "reduce motion" means the list is simply at the
 * top, not that it travels there more discreetly.
 */
@Composable
fun LazyListState.ObeyScrollToTop(route: String, scrollToTop: SharedFlow<String>) {
    val instant = LocalAnimations.current == AnimationLevel.OFF
    LaunchedEffect(route, instant) {
        scrollToTop.collect { requested ->
            if (requested != route) return@collect
            if (instant) scrollToItem(0) else animateScrollToItem(0)
        }
    }
}
