package tv.own.owntv.mobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The brand glyph set, drawn on the same normalised 24×24 grid as the television's `OwnTVIcon` so
 * that the two apps look like one product.
 *
 * These are `ImageVector`s rather than the television's `Canvas` composable, deliberately. Every call
 * site here already passes an `ImageVector` to Material's `Icon`, which is what applies the tint, the
 * content description and the RTL mirroring — a second drawing composable would have meant touching
 * ~150 call sites and re-implementing all three. `autoMirror` in particular is free this way, and the
 * app ships right-to-left languages.
 *
 * The stroke is a shade heavier than Material's: these are read at 24 dp on a 400+ ppi panel at arm's
 * length, not at 40 dp across a room, and the thin default is what disappears at that size.
 */
object MobileIcons {

    // ---- Navigation and structure -------------------------------------------------------------

    val ArrowBack: ImageVector by lazy {
        glyph(mirror = true) {
            line(20f, 12f, 4f, 12f); line(4f, 12f, 10f, 6f); line(4f, 12f, 10f, 18f)
        }
    }
    val KeyboardArrowRight: ImageVector by lazy {
        glyph(mirror = true) { line(9f, 5f, 16f, 12f, 9f, 19f) }
    }
    val KeyboardArrowUp: ImageVector by lazy {
        glyph() { line(5f, 15f, 12f, 8f, 19f, 15f) }
    }
    val KeyboardArrowDown: ImageVector by lazy {
        glyph() { line(5f, 9f, 12f, 16f, 19f, 9f) }
    }
    val KeyboardDoubleArrowUp: ImageVector by lazy {
        glyph() { line(5f, 12f, 12f, 5f, 19f, 12f); line(5f, 20f, 12f, 13f, 19f, 20f) }
    }
    val KeyboardDoubleArrowDown: ImageVector by lazy {
        glyph() { line(5f, 4f, 12f, 11f, 19f, 4f); line(5f, 12f, 12f, 19f, 19f, 12f) }
    }
    val Close: ImageVector by lazy {
        glyph() { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
    }
    val MoreHoriz: ImageVector by lazy {
        glyph() { dot(5.5f, 12f, 1.7f); dot(12f, 12f, 1.7f); dot(18.5f, 12f, 1.7f) }
    }
    val Home: ImageVector by lazy {
        glyph() {
            line(12f, 3f, 3f, 12f); line(12f, 3f, 21f, 12f)
            box(5f, 12f, 19f, 21f, 2f)
            line(10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f)
        }
    }
    val OpenInNew: ImageVector by lazy {
        glyph(mirror = true) {
            line(19f, 13.5f, 19f, 19.5f, 4.5f, 19.5f, 4.5f, 5f, 10.5f, 5f)
            line(13.5f, 4f, 20f, 4f, 20f, 10.5f)
            line(11f, 13f, 20f, 4f)
        }
    }
    val OpenInFull: ImageVector by lazy {
        glyph() {
            line(6f, 18f, 18f, 6f)
            line(12.5f, 6f, 18f, 6f, 18f, 11.5f)
            line(11.5f, 18f, 6f, 18f, 6f, 12.5f)
        }
    }

    // ---- Content ------------------------------------------------------------------------------

    val LiveTv: ImageVector by lazy {
        glyph() { box(3f, 8f, 21f, 21f, 2.5f); line(8f, 8f, 12f, 3f); line(16f, 8f, 12f, 3f) }
    }
    val Tv: ImageVector by lazy {
        glyph() { box(3f, 4f, 21f, 17f, 2.5f); line(8f, 21f, 16f, 21f); line(12f, 17f, 12f, 21f) }
    }
    val Movie: ImageVector by lazy {
        glyph() {
            box(3f, 9f, 21f, 20f, 2f)
            line(3f, 9f, 21f, 6f)
            line(7.5f, 9f, 9f, 6.2f); line(12f, 9f, 13.5f, 5.8f); line(16.5f, 9f, 18f, 5.5f)
        }
    }
    val Theaters: ImageVector by lazy {
        glyph() { box(6f, 4f, 21f, 14f, 2f); line(3f, 8f, 3f, 20f, 18f, 20f) }
    }
    val VideoLibrary: ImageVector by lazy {
        glyph() {
            box(7f, 3f, 21f, 15f, 2f)
            solid(11.5f, 6f, 16f, 9f, 11.5f, 12f)
            line(3f, 7f, 3f, 21f, 17f, 21f)
        }
    }
    val Image: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 19f, 2f)
            dot(8f, 10f, 1.2f)
            line(4.5f, 18f, 9.5f, 12f, 13f, 15f, 16f, 11f, 19.5f, 18f)
        }
    }
    val Folder: ImageVector by lazy {
        glyph() {
            outline { moveTo(3f, 19.5f); lineTo(3f, 5.5f); lineTo(9.5f, 5.5f); lineTo(11.5f, 8.5f); lineTo(21f, 8.5f); lineTo(21f, 19.5f); close() }
        }
    }
    val Cloud: ImageVector by lazy {
        glyph() {
            dot(8.6f, 14.4f, 4.2f); dot(12.6f, 12.2f, 5.2f); dot(16.8f, 15f, 3.9f)
            bar(8.6f, 14.4f, 16.8f, 18.6f)
        }
    }

    // ---- Actions ------------------------------------------------------------------------------

    val Add: ImageVector by lazy {
        glyph() { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
    }
    /** Four tiles — Multiview, and the same idea as the television's LIST_GRID mark. */
    val GridView: ImageVector by lazy {
        glyph() {
            box(4f, 4f, 11f, 11f, 1.5f)
            box(13f, 4f, 20f, 11f, 1.5f)
            box(4f, 13f, 11f, 20f, 1.5f)
            box(13f, 13f, 20f, 20f, 1.5f)
        }
    }
    val Check: ImageVector by lazy {
        glyph() { line(4f, 13f, 9.5f, 18.5f, 20f, 6f) }
    }
    val CheckCircle: ImageVector by lazy {
        glyph() { ring(12f, 12f, 9f); line(7.5f, 12.3f, 10.7f, 15.5f, 16.5f, 8.8f) }
    }
    val RadioButtonUnchecked: ImageVector by lazy {
        glyph() { ring(12f, 12f, 9f) }
    }
    val Delete: ImageVector by lazy {
        glyph() {
            line(3.5f, 6.5f, 20.5f, 6.5f)
            box(6f, 6.5f, 18f, 21f, 2f)
            line(9.5f, 3.5f, 14.5f, 3.5f)
            line(10f, 11f, 10f, 17.5f); line(14f, 11f, 14f, 17.5f)
        }
    }
    val Edit: ImageVector by lazy {
        glyph() {
            outline { moveTo(4f, 20f); lineTo(4.9f, 16.3f); lineTo(16.6f, 4.6f); lineTo(19.4f, 7.4f); lineTo(7.7f, 19.1f); close() }
            line(14.2f, 7f, 17f, 9.8f)
        }
    }
    val Save: ImageVector by lazy {
        glyph() {
            box(3.5f, 3.5f, 20.5f, 20.5f, 2f)
            bar(8f, 3.5f, 15f, 9f)
            box(7f, 13f, 17f, 20.5f, 1f)
        }
    }
    val Search: ImageVector by lazy {
        glyph() { ring(10.5f, 10.5f, 6.5f); line(15.5f, 15.5f, 20f, 20f) }
    }
    val Refresh: ImageVector by lazy {
        glyph() { arc(12f, 12f, 8.5f, 0f, 314f); line(20.8f, 3.6f, 20.8f, 8.6f, 15.8f, 8.6f) }
    }
    val Sync: ImageVector by lazy {
        glyph() {
            arc(12f, 12f, 8.5f, -160f, 130f)
            line(2f, 8.5f, 3.5f, 3.6f, 8.4f, 5.1f)
            arc(12f, 12f, 8.5f, 20f, 130f)
            line(22f, 15.5f, 20.5f, 20.4f, 15.6f, 18.9f)
        }
    }
    val Restore: ImageVector by lazy {
        glyph() {
            arc(12f, 12f, 8.5f, 180f, -314f)
            line(3.2f, 3.6f, 3.2f, 8.6f, 8.2f, 8.6f)
            line(12f, 7.5f, 12f, 12f, 15.5f, 14f)
        }
    }
    val History: ImageVector by lazy {
        glyph() {
            arc(12f, 12f, 9f, -60f, 300f)
            line(16.5f, 4.2f, 16.5f, 9.2f, 11.5f, 9.2f)
            line(12f, 7f, 12f, 12f, 16f, 14f)
        }
    }
    /**
     * Catch-up: a television with a replay loop inside it, and a single antenna.
     *
     * Drawn to match the television app's `OwnTVIcon.CATCHUP` (H3). It used to be [History] here and
     * a screen-with-a-loop there — the same function wearing two faces, which is one of the four
     * mismatches Feature H exists to remove. One antenna rather than a V: it says "television" for
     * the cost of a single line and stays clear of the loop's arrowhead.
     */
    val Catchup: ImageVector by lazy {
        glyph() {
            line(2.6f, 6.8f, 21.4f, 6.8f, 21.4f, 19.8f, 2.6f, 19.8f, 2.6f, 6.8f)
            line(12.6f, 6.8f, 16.2f, 3.2f)
            arc(12f, 13.4f, 4.3f, -30f, 285f)
        }
    }

    /**
     * Over-ear headphones — a headband arc and two earcups.
     *
     * Drawn to match the television's `OwnTVIcon.HEADPHONES` (H3). It was a [MusicNote] here, which
     * says "audio" but not "sound only, no picture", and the television has never used one.
     */
    val Headphones: ImageVector by lazy {
        glyph() {
            arc(12f, 13f, 8f, 180f, 180f)
            line(4f, 13f, 4f, 19f)
            dot(4f, 16.5f, 2.2f)
            line(20f, 13f, 20f, 19f)
            dot(20f, 16.5f, 2.2f)
        }
    }

    /**
     * Share: three nodes and the two lines between them.
     *
     * Drawn to match the television's `OwnTVIcon.SHARE` (H3). Reporting a stream was a [BugReport]
     * here and this there; the television's is the reference, and "share this readout" is nearer to
     * what the button does than "file a bug" is.
     */
    val Share: ImageVector by lazy {
        glyph() {
            ring(6f, 12f, 2.4f)
            ring(18f, 6f, 2.4f)
            ring(18f, 18f, 2.4f)
            line(8f, 11f, 16f, 7f)
            line(8f, 13f, 16f, 17f)
        }
    }

    val Schedule: ImageVector by lazy {
        glyph() { ring(12f, 12f, 9f); line(12f, 6.5f, 12f, 12f, 16.5f, 14f) }
    }
    val CalendarMonth: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 21f, 2f)
            line(3f, 10f, 21f, 10f)
            line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
            dot(8f, 14f, 1.1f); dot(12f, 14f, 1.1f); dot(16f, 14f, 1.1f)
            dot(8f, 18f, 1.1f); dot(12f, 18f, 1.1f)
        }
    }

    // ---- Lists --------------------------------------------------------------------------------

    val PlaylistAdd: ImageVector by lazy {
        glyph() {
            line(4f, 7f, 16f, 7f); line(4f, 12f, 16f, 12f); line(4f, 17f, 11f, 17f)
            line(18f, 13.5f, 18f, 20.5f); line(14.5f, 17f, 21.5f, 17f)
        }
    }
    val PlaylistPlay: ImageVector by lazy {
        glyph() {
            line(4f, 7f, 16f, 7f); line(4f, 12f, 16f, 12f); line(4f, 17f, 11f, 17f)
            solid(15f, 14f, 21f, 17f, 15f, 20f)
        }
    }
    val FormatListBulleted: ImageVector by lazy {
        glyph() {
            dot(4.5f, 7f, 1.4f); dot(4.5f, 12f, 1.4f); dot(4.5f, 17f, 1.4f)
            line(9f, 7f, 20f, 7f); line(9f, 12f, 20f, 12f); line(9f, 17f, 20f, 17f)
        }
    }
    val ViewList: ImageVector by lazy {
        glyph() { box(3f, 5f, 21f, 19f, 2f); line(9f, 5f, 9f, 19f); line(9f, 12f, 21f, 12f) }
    }
    val SwapVert: ImageVector by lazy {
        glyph() {
            line(8f, 20f, 8f, 4f); line(4.8f, 7.2f, 8f, 4f, 11.2f, 7.2f)
            line(16f, 4f, 16f, 20f); line(12.8f, 16.8f, 16f, 20f, 19.2f, 16.8f)
        }
    }
    val SwapHoriz: ImageVector by lazy {
        glyph() {
            line(4f, 9f, 18f, 9f); line(15f, 6.5f, 18f, 9f, 15f, 11.5f)
            line(20f, 15f, 6f, 15f); line(9f, 12.5f, 6f, 15f, 9f, 17.5f)
        }
    }

    // ---- Marks --------------------------------------------------------------------------------

    val Star: ImageVector by lazy { glyph() { star(filled = true) } }
    val StarBorder: ImageVector by lazy { glyph() { star(filled = false) } }
    val Favorite: ImageVector by lazy { glyph() { heart(filled = true) } }
    val FavoriteBorder: ImageVector by lazy { glyph() { heart(filled = false) } }
    val Info: ImageVector by lazy {
        glyph() { ring(12f, 12f, 9f); dot(12f, 7.6f, 1.2f); line(12f, 11f, 12f, 16.5f) }
    }
    val BugReport: ImageVector by lazy {
        glyph() {
            box(7.5f, 8f, 16.5f, 20f, 4.5f)
            line(7.5f, 12f, 3.5f, 12f); line(16.5f, 12f, 20.5f, 12f)
            line(7.5f, 17f, 4f, 19f); line(16.5f, 17f, 20f, 19f)
            line(9.5f, 6.5f, 8f, 4f); line(14.5f, 6.5f, 16f, 4f)
        }
    }
    val Build: ImageVector by lazy {
        glyph() {
            outline {
                moveTo(14.4f, 3.2f); lineTo(17.6f, 6.4f); lineTo(20.8f, 3.2f)
                arcTo(6.5f, 6.5f, 0f, true, true, 12.4f, 12.6f)
                lineTo(4.6f, 20.4f); lineTo(2.6f, 18.4f); lineTo(10.4f, 10.6f)
                arcTo(6.5f, 6.5f, 0f, true, true, 14.4f, 3.2f); close()
            }
        }
    }
    val Person: ImageVector by lazy {
        glyph() { ring(12f, 8f, 3.6f); arc(12f, 20f, 7f, 180f, 180f) }
    }
    val People: ImageVector by lazy {
        glyph() {
            ring(9f, 8f, 3.3f); arc(9f, 19f, 6f, 180f, 180f)
            ring(17f, 7.5f, 2.6f); arc(17.5f, 18f, 4.6f, 200f, 130f)
        }
    }

    // ---- Player -------------------------------------------------------------------------------

    val PlayArrow: ImageVector by lazy { glyph() { solid(8f, 5f, 19f, 12f, 8f, 19f) } }
    val PlayCircle: ImageVector by lazy {
        glyph() { ring(12f, 12f, 9f); solid(10f, 7.8f, 17f, 12f, 10f, 16.2f) }
    }
    val Pause: ImageVector by lazy {
        glyph() { bar(8f, 5f, 10.6f, 19f); bar(13.4f, 5f, 16f, 19f) }
    }
    val FastForward: ImageVector by lazy {
        glyph() { solid(4f, 6f, 11f, 12f, 4f, 18f); solid(13f, 6f, 20f, 12f, 13f, 18f) }
    }
    val FastRewind: ImageVector by lazy {
        glyph() { solid(11f, 6f, 4f, 12f, 11f, 18f); solid(20f, 6f, 13f, 12f, 20f, 18f) }
    }
    val SkipNext: ImageVector by lazy {
        glyph() { solid(6f, 6f, 15f, 12f, 6f, 18f); bar(16f, 6f, 18.5f, 18f) }
    }
    val SkipPrevious: ImageVector by lazy {
        glyph() { solid(18f, 6f, 9f, 12f, 18f, 18f); bar(5.5f, 6f, 8f, 18f) }
    }
    val Download: ImageVector by lazy {
        glyph() {
            line(12f, 3f, 12f, 15f); line(7f, 10f, 12f, 15f, 17f, 10f); line(5f, 20f, 19f, 20f)
        }
    }
    val Subtitles: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 19f, 2.5f); line(6f, 14f, 11f, 14f); line(13f, 14f, 18f, 14f)
        }
    }
    val ClosedCaption: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 19f, 2.5f)
            arc(9.2f, 12f, 2.7f, -35f, -290f)
            arc(15.6f, 12f, 2.7f, -35f, -290f)
        }
    }
    val VolumeUp: ImageVector by lazy {
        glyph() {
            speaker()
            arc(11f, 12f, 3.6f, -52f, 104f)
            arc(11f, 12f, 6.5f, -52f, 104f)
        }
    }
    val VolumeOff: ImageVector by lazy {
        glyph() { speaker(); line(14f, 9f, 20f, 15f); line(20f, 9f, 14f, 15f) }
    }
    val MusicNote: ImageVector by lazy { glyph() { note() } }
    val Audiotrack: ImageVector by lazy { glyph() { note() } }
    val AspectRatio: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 19f, 2.5f)
            line(7f, 11f, 7f, 9f, 9f, 9f)
            line(17f, 13f, 17f, 15f, 15f, 15f)
        }
    }
    val PictureInPictureAlt: ImageVector by lazy {
        glyph() { box(3f, 5f, 21f, 19f, 2.5f); bar(12.5f, 12f, 19f, 17f) }
    }
    // No Cast glyph: the cast button is the platform's own MediaRouteButton, which the Cast SDK
    // drives — see CastRouteButton.kt. Drawing our own would leave it not knowing when a receiver
    // is in range, which is the only interesting thing about that button.

    // ---- Settings -----------------------------------------------------------------------------

    val Settings: ImageVector by lazy {
        glyph() {
            ring(12f, 12f, 6.5f); ring(12f, 12f, 2.7f)
            line(12f, 2.5f, 12f, 5.5f); line(12f, 18.5f, 12f, 21.5f)
            line(2.5f, 12f, 5.5f, 12f); line(18.5f, 12f, 21.5f, 12f)
            line(5.3f, 5.3f, 7.3f, 7.3f); line(16.7f, 16.7f, 18.7f, 18.7f)
            line(18.7f, 5.3f, 16.7f, 7.3f); line(7.3f, 16.7f, 5.3f, 18.7f)
        }
    }
    val Tune: ImageVector by lazy {
        glyph() {
            line(4f, 8f, 20f, 8f); line(4f, 16f, 20f, 16f)
            dot(9f, 8f, 2.6f); dot(15f, 16f, 2.6f)
        }
    }
    val Palette: ImageVector by lazy {
        glyph() {
            arc(12f, 12f, 9f, 110f, 320f)
            dot(8.5f, 8f, 1.3f); dot(13f, 6.5f, 1.3f); dot(16.5f, 9.5f, 1.3f)
        }
    }
    val TextFields: ImageVector by lazy {
        glyph() {
            line(2f, 19f, 7f, 5f, 12f, 19f); line(3.7f, 14.6f, 10.3f, 14.6f)
            line(14.5f, 19f, 17.7f, 11f, 21f, 19f); line(15.6f, 16.2f, 19.8f, 16.2f)
        }
    }
    val Title: ImageVector by lazy {
        glyph() { line(4f, 6f, 20f, 6f); line(12f, 6f, 12f, 19f) }
    }
    val Storage: ImageVector by lazy {
        glyph() {
            box(3f, 5f, 21f, 9.5f, 1.5f); box(3f, 14.5f, 21f, 19f, 1.5f)
            dot(6.5f, 7.25f, 1.1f); dot(6.5f, 16.75f, 1.1f)
        }
    }
    val SdStorage: ImageVector by lazy {
        glyph() {
            outline {
                moveTo(7f, 21f); lineTo(7f, 8f); lineTo(12f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); close()
            }
            line(10f, 6.5f, 10f, 9.5f); line(13f, 6.5f, 13f, 9.5f); line(16f, 6.5f, 16f, 9.5f)
        }
    }
    val Wifi: ImageVector by lazy {
        glyph() {
            arc(12f, 19f, 10f, -150f, 120f)
            arc(12f, 19f, 6.3f, -145f, 110f)
            dot(12f, 18.5f, 1.7f)
        }
    }
    val VisibilityOff: ImageVector by lazy {
        glyph() {
            arc(12f, 18f, 10.5f, -160f, 140f)
            arc(12f, 6f, 10.5f, 20f, 140f)
            ring(12f, 12f, 3.2f)
            line(4.5f, 19.5f, 19.5f, 4.5f)
        }
    }

    // ---- Atmosphere ---------------------------------------------------------------------------

    val WbSunny: ImageVector by lazy { glyph() { ring(12f, 12f, 4.6f); rays() } }
    val BrightnessMedium: ImageVector by lazy {
        glyph() {
            ring(12f, 12f, 4.6f); rays()
            fill { moveTo(12f, 7.4f); arcTo(4.6f, 4.6f, 0f, false, true, 12f, 16.6f); close() }
        }
    }
    val DarkMode: ImageVector by lazy {
        glyph() {
            ring(12f, 12f, 8f)
            fill { moveTo(12f, 4f); arcTo(8f, 8f, 0f, false, true, 12f, 20f); close() }
        }
    }
    val Bedtime: ImageVector by lazy {
        glyph() {
            fill {
                moveTo(19.5f, 15.6f)
                arcTo(9f, 9f, 0f, true, true, 8.4f, 4.5f)
                arcTo(7.4f, 7.4f, 0f, false, false, 19.5f, 15.6f)
                close()
            }
        }
    }
    val AcUnit: ImageVector by lazy {
        glyph() {
            line(12f, 2.5f, 12f, 21.5f); line(4f, 7f, 20f, 17f); line(20f, 7f, 4f, 17f)
            line(9.2f, 5.2f, 12f, 8f, 14.8f, 5.2f)
            line(9.2f, 18.8f, 12f, 16f, 14.8f, 18.8f)
        }
    }
    val WaterDrop: ImageVector by lazy {
        glyph() {
            fill {
                moveTo(12f, 2.8f); lineTo(17.6f, 11.4f)
                arcTo(6.7f, 6.7f, 0f, true, true, 6.4f, 11.4f); close()
            }
        }
    }
    val Grain: ImageVector by lazy {
        glyph() {
            dot(6f, 6f, 1.5f); dot(12f, 6f, 1.5f); dot(18f, 6f, 1.5f)
            dot(9f, 12f, 1.5f); dot(15f, 12f, 1.5f)
            dot(6f, 18f, 1.5f); dot(12f, 18f, 1.5f); dot(18f, 18f, 1.5f)
        }
    }
    val Bolt: ImageVector by lazy {
        glyph() { solid(13.5f, 2f, 6f, 13.5f, 11f, 13.5f, 10.5f, 22f, 18f, 10.5f, 13f, 10.5f) }
    }
    /** A globe: the language picker. Meridians drawn as polylines — the grid has no ellipse. */
    val Translate: ImageVector by lazy {
        glyph() {
            ring(12f, 12f, 8.6f)
            line(3.4f, 12f, 20.6f, 12f)
            line(12f, 3.4f, 8.4f, 7f, 7.4f, 12f, 8.4f, 17f, 12f, 20.6f)
            line(12f, 3.4f, 15.6f, 7f, 16.6f, 12f, 15.6f, 17f, 12f, 20.6f)
        }
    }
    val AutoAwesome: ImageVector by lazy {
        glyph() {
            solid(
                12f, 2.5f, 14.2f, 9.8f, 21.5f, 12f, 14.2f, 14.2f,
                12f, 21.5f, 9.8f, 14.2f, 2.5f, 12f, 9.8f, 9.8f,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// The grid. Everything above is written in 24×24 units; everything below turns that into a vector.
// -------------------------------------------------------------------------------------------------

/** Heavier than Material's 2.0: read at 24 dp on a dense panel, the default stroke vanishes. */
private const val W = 2.2f
private val Ink = SolidColor(Color.Black)

private class Grid(private val builder: ImageVector.Builder) {

    /** A polyline through the given `x, y` pairs. */
    fun line(vararg v: Float) = outline {
        moveTo(v[0], v[1])
        for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1])
    }

    /** A closed, filled polygon through the given `x, y` pairs. */
    fun solid(vararg v: Float) = fill {
        moveTo(v[0], v[1])
        for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1])
        close()
    }

    fun bar(l: Float, t: Float, r: Float, b: Float) = solid(l, t, r, t, r, b, l, b)

    fun ring(cx: Float, cy: Float, r: Float) = outline { circle(cx, cy, r) }

    fun dot(cx: Float, cy: Float, r: Float) = fill { circle(cx, cy, r) }

    fun box(l: Float, t: Float, r: Float, b: Float, rad: Float) = outline {
        moveTo(l + rad, t); lineTo(r - rad, t); arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad); arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b); arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad); arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    /** An open arc, in degrees clockwise from three o'clock — the same convention as `drawArc`. */
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) = outline {
        fun rad(d: Float) = (d * Math.PI / 180.0).toFloat()
        moveTo(cx + r * cos(rad(startDeg)), cy + r * sin(rad(startDeg)))
        val end = startDeg + sweepDeg
        // A single arc segment cannot cross 360°, and anything past a half turn needs the long-way
        // flag; splitting at the halfway point keeps both facts true without a special case.
        val mid = startDeg + sweepDeg / 2f
        arcTo(r, r, 0f, abs(sweepDeg / 2f) > 180f, sweepDeg > 0f, cx + r * cos(rad(mid)), cy + r * sin(rad(mid)))
        arcTo(r, r, 0f, abs(sweepDeg / 2f) > 180f, sweepDeg > 0f, cx + r * cos(rad(end)), cy + r * sin(rad(end)))
    }

    /** The five-point star, shared by the rating mark and its empty twin. */
    fun star(filled: Boolean) {
        val pts = FloatArray(20)
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 9f else 3.9f
            val a = (Math.PI / 5 * i - Math.PI / 2).toFloat()
            pts[i * 2] = 12f + r * cos(a)
            pts[i * 2 + 1] = 12f + r * sin(a)
        }
        if (filled) solid(*pts) else line(*pts, pts[0], pts[1])
    }

    /** A heart, because a star already means *rating* on a poster. */
    fun heart(filled: Boolean) {
        val body: PathBuilder.() -> Unit = {
            moveTo(12f, 20.5f)
            curveTo(6.5f, 16.2f, 3f, 12.8f, 3f, 9f)
            curveTo(3f, 6f, 5.3f, 3.8f, 8f, 3.8f)
            curveTo(10f, 3.8f, 11.4f, 5f, 12f, 6.3f)
            curveTo(12.6f, 5f, 14f, 3.8f, 16f, 3.8f)
            curveTo(18.7f, 3.8f, 21f, 6f, 21f, 9f)
            curveTo(21f, 12.8f, 17.5f, 16.2f, 12f, 20.5f)
            close()
        }
        if (filled) fill(body) else outline(body)
    }

    /** The speaker body the two volume glyphs share. */
    fun speaker() = solid(3f, 9f, 7f, 9f, 11f, 5f, 11f, 19f, 7f, 15f, 3f, 15f)

    /** A quaver — the audio track, told apart from the speaker at a glance. */
    fun note() {
        dot(8.5f, 17.5f, 3f)
        line(11.5f, 17.5f, 11.5f, 5f)
        line(11.5f, 5f, 16.5f, 7f)
        line(11.5f, 8.5f, 16.5f, 10.5f)
    }

    /** Eight rays around a 4.6 disc. */
    fun rays() {
        for (i in 0 until 8) {
            val a = (Math.PI / 4 * i).toFloat()
            line(12f + 6.4f * cos(a), 12f + 6.4f * sin(a), 12f + 8.6f * cos(a), 12f + 8.6f * sin(a))
        }
    }

    fun outline(build: PathBuilder.() -> Unit) = builder.path(
        stroke = Ink,
        strokeLineWidth = W,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build,
    )

    fun fill(build: PathBuilder.() -> Unit) = builder.path(fill = Ink, pathBuilder = build)
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

/**
 * One glyph. The vector carries no name — a per-icon string here would be eighty-one literals for the
 * i18n gate to police, and nothing reads it.
 */
private fun glyph(mirror: Boolean = false, body: Grid.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirror,
    ).apply { Grid(this).body() }.build()
