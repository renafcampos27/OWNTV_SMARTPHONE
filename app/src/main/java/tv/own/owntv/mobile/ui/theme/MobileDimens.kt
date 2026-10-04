package tv.own.owntv.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing for a screen held at arm's length, not across a room. The TV app's values
 * are roughly double these: 48 dp screen padding becomes 16 (there is no overscan to avoid),
 * 180 dp posters become 100 (three columns have to fit a 360 dp phone).
 */
object MobileDimens {
    /** Material's accessibility floor. Nothing tappable is ever smaller. */
    val TouchTarget = 48.dp

    val ScreenPaddingH = 16.dp
    val ScreenPaddingV = 12.dp

    val GapTiny = 4.dp
    val GapSmall = 8.dp
    val GapMedium = 16.dp
    val GapLarge = 24.dp

    val CardCorner = 12.dp
    val SheetCorner = 28.dp

    /**
     * The floating shell: the bar, the page and the navigation are panes standing off the screen
     * edges, with the wallpaper running around them rather than only behind them. Glass only reads
     * as glass when there is something beside it to compare it to.
     */
    val ShellInset = 11.dp
    val ShellGap = 9.dp
    val TopBarCorner = 20.dp
    val PageCorner = 24.dp
    val NavCorner = 22.dp
    val NavIslandHeight = 64.dp

    /**
     * A run of rows is one rounded block on the page, with its heading standing above it — the block
     * is what groups them, so the rows inside it carry no pane of their own.
     */
    val GroupCorner = 18.dp
    val GroupGap = 11.dp

    /** The page's own margin inside its pane. Narrow, because the pane is already inset. */
    val PagePaddingH = 10.dp

    val PosterWidthPortrait = 100.dp
    val PosterWidthLandscape = 120.dp
    val PosterArtCorner = 10.dp
    val PosterProgressHeight = 3.dp

    /** The poster that overlaps a detail header — the tile the user tapped, arrived. */
    val DetailPosterHeight = 132.dp

    /**
     * A tile's own margin, and the sliver that keeps one title off the next. Both are tiny on
     * purpose: the artwork is the tile, and every dp spent framing it is a dp off the picture.
     */
    val PosterPadding = 5.dp
    val PosterPaddingBottom = 7.dp
    val PosterTextInset = 1.dp

    /** The air between two tiles, in a grid or along a rail. */
    val GridGap = 9.dp

    /** Row height for a list of channels or settings — one line of title plus one of subtitle. */
    val ListRowHeight = 56.dp
    val ListRowIconSize = 34.dp

    /** A row's own margin inside its group, narrower than a screen's because the group is inset. */
    val ListRowPaddingH = 12.dp
    val ListRowPaddingV = 11.dp
    val ListRowIconGap = 12.dp
    val ListRowIconCorner = 11.dp

    /** The glyph inside the plate. Smaller than Material's 24 dp, so the plate frames it. */
    val ListRowIconGlyph = 20.dp

    /**
     * A sheet is tighter than a page. Its rows are a menu the thumb runs down, not a list to read,
     * so they lose the icon plate, the 56 dp floor and most of the air: Material's own sheet spends
     * 44 dp on the grab handle alone, which is most of a row given away before anything is said.
     */
    val SheetPaddingH = 12.dp
    val SheetPaddingBottom = 14.dp
    val SheetGap = 9.dp
    val SheetRowsCorner = 16.dp
    val SheetRowPaddingH = 14.dp
    val SheetRowPaddingV = 12.dp
    val SheetRowIconSize = 18.dp
    val GrabWidth = 36.dp
    val GrabHeight = 4.dp
    val GrabPaddingTop = 8.dp
    val GrabPaddingBottom = 2.dp

    /**
     * The filter strip above a list. A chip is a label with a rim, not a button: it is read across
     * in one sweep, so it stays short and the strip stays a strip rather than a second toolbar.
     */
    val ChipHeight = 30.dp
    val ChipPaddingH = 13.dp
    val ChipGap = 7.dp
    /** The page's margin plus the strip's own, so the first chip lines up with the pane below it. */
    val ChipRowPaddingH = PagePaddingH + 4.dp
    val ChipRowPaddingTop = 2.dp
    val ChipRowPaddingBottom = 11.dp
}
