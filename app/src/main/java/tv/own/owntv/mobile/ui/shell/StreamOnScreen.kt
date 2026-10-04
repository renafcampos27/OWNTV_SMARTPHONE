package tv.own.owntv.mobile.ui.shell

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Whether a screen is drawing the running stream itself.
 *
 * The shell hides the mini player wherever the picture is already on display — a second view of it
 * would take the surface away from the first, and a floating window over the very thing it is a
 * window onto is a smudge. The shell used to work this out from the route alone, which was true for
 * as long as a channel could only be watched on a route of its own. At expanded width it is watched
 * *inside* the Live TV route, in the pane beside the list, and the route no longer says so.
 *
 * So the screen says so instead. The default is a throwaway state, so a preview or a test that
 * composes a screen without the shell around it still works.
 */
val LocalStreamOnScreen = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/**
 * Whether the user actually asked for the stream to outlive the screen it was playing on.
 *
 * The mini player used to appear on its own, from nothing more than "something is playing and you
 * cannot see it". That is not a request: opening a channel from the list starts a preview, and
 * walking away to another tab then put a floating window over whatever the user went to look at —
 * a window they never asked for and had to close every time.
 *
 * **Only the full screen player's own buttons set this** — the mini-player button, the swipe down,
 * and Sound only, each of which says on the face of it that the stream is meant to keep going. Back
 * out of the player does not, and neither does leaving a channel preview, which stops instead: a
 * stream nobody can see is one of the provider's allowed connections held open for nothing.
 */
val LocalMiniRequested = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }
