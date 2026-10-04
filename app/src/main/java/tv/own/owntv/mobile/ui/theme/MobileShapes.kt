package tv.own.owntv.mobile.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * A rounded rectangle whose corners have **continuous curvature**: the curve starts further along the
 * edge and eases into the turn instead of meeting the straight side at a sudden change of radius.
 *
 * It is what makes the Aurora glass read as a moulded pane rather than a rectangle with its corners
 * filed off, and the difference is most visible exactly where this app uses it — large sheets and
 * panels against a photograph. The maths is AndroidX's; a hand-rolled approximation goes visibly
 * wrong at small radii, which is the one place a corner is looked at closely.
 */
/** Below this, a corner is a rectangle to the eye and a division by nothing to the maths. */
private const val MIN_RADIUS_PX = 0.5f

class SquircleShape(
    private val corner: Dp,
    // 0 is an ordinary rounded corner, 1 spreads the curve as far along the edge as it will go.
    private val smoothing: Float = 0.6f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // A collapsing top bar passes through zero height on its way out, and a polygon with no area
        // has no corners to round — asking for one throws and takes the app with it. A size can also
        // arrive infinite or NaN from a layer mid-animation, which `<= 0f` lets straight through and
        // which reaches the same crash ("Can't get the direction of a 0-length vector") one step
        // later, so both are turned away here.
        if (!size.width.isFinite() || !size.height.isFinite() || size.minDimension <= 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val radius = with(density) { corner.toPx() }
            // A corner cannot be deeper than half the shorter side, and a squircle's curve reaches
            // further than its radius, so leave the smoothing its room too.
            .coerceAtMost(minOf(size.width, size.height) / 2f / (1f + smoothing))
        // A corner that rounds to nothing is a plain rectangle, and asking the rounding maths for one
        // is what produces the zero-length vector.
        if (radius < MIN_RADIUS_PX) return Outline.Rectangle(size.toRect())
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            rounding = CornerRounding(radius = radius, smoothing = smoothing),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }
}

/** The shape of every card and panel in the app. */
val MobileCardShape: Shape = SquircleShape(MobileDimens.CardCorner)

/** Artwork's own corner, a touch tighter than a card's — a poster is the picture, not a plate. */
val MobilePosterShape: Shape = SquircleShape(MobileDimens.PosterArtCorner)

/** The little plate a row's icon sits on. */
val MobileChipShape: Shape = SquircleShape(MobileDimens.ListRowIconCorner)

/** The block a run of rows is grouped into. */
val MobileGroupShape: Shape = SquircleShape(MobileDimens.GroupCorner)

// The floating shell's four panes. Each stands clear of the screen edges, so each is rounded on all
// four corners — including the sheet, which no longer sits on the bottom edge.
val MobileTopBarShape: Shape = SquircleShape(MobileDimens.TopBarCorner)
val MobilePageShape: Shape = SquircleShape(MobileDimens.PageCorner)
val MobileNavShape: Shape = SquircleShape(MobileDimens.NavCorner)
val MobileSheetShape: Shape = SquircleShape(MobileDimens.SheetCorner)

/** The plate a sheet's rows stand on, inside the sheet's own corner. */
val MobileSheetRowsShape: Shape = SquircleShape(MobileDimens.SheetRowsCorner)
