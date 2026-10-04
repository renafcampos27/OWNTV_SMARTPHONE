package tv.own.owntv.mobile.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The two facts the activity and the player screen have to agree on about Picture-in-Picture.
 *
 * Only the activity can enter PiP, and only the player screen knows whether the picture is what the
 * user is looking at — so the decision needs one small piece of state that outlives neither of them
 * alone. Deliberately two booleans and nothing else: everything else about PiP is already answerable
 * from the player.
 */
class PipController {

    /** The full screen player owns the display. Home pressed now means "keep watching", not "leave". */
    val playerOnScreen = MutableStateFlow(false)

    /** The app is running in the little window — no controls, no gestures, no bars. */
    val inPip = MutableStateFlow(false)

    /**
     * Where the picture is on screen, in window coordinates, while the player is showing it.
     *
     * Handed to the system as the little window's *source*, which is what turns entering PiP into
     * the picture shrinking into the corner instead of the whole screen cross-fading into it. Null
     * whenever nothing is drawing a picture, and the system simply falls back to the cross-fade.
     *
     * A plain field rather than a flow: it is read once, inside the call that enters PiP, and a
     * position that changed a frame ago is not worth a recomposition anywhere.
     */
    var videoBounds: android.graphics.Rect? = null

    /**
     * The playback notification was tapped: put the full screen player back on screen.
     *
     * A one-shot, cleared by the shell once it has navigated. It has to travel this way because the
     * two ends cannot reach each other: the notification arrives as an Intent, which only the activity
     * sees, and the player is a navigation destination, which only the shell can reach.
     */
    val openPlayerRequested = MutableStateFlow(false)
}
