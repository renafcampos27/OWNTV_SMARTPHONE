package tv.own.owntv.mobile.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.mobile.ui.theme.GlassNest
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobileSheetShape
import tv.own.owntv.mobile.ui.theme.glassSurface
import kotlin.math.roundToInt

/** Where a sheet can come to rest. */
private enum class SheetAnchor { HIDDEN, HALF, EXPANDED }

/** How far the content starts below where it ends up. Enough to read as weight, not as a slide. */
private const val SETTLE_DP = 18f

/** The grab handle, as faint as the mockup's. */
private const val GRAB_ALPHA = 0.34f

/** How dark the app goes behind an open sheet. */
private const val SCRIM_ALPHA = 0.5f

/** How far the back gesture shrinks the sheet before it lets go of it. */
private const val BACK_SCALE = 0.92f

/** A sheet never spans a tablet: past this it is a panel at the bottom, not a wall. */
private val SheetMaxWidth = 640.dp

/** One open sheet's registration with the host. Mutable, because the caller keeps recomposing. */
@Stable
internal class SheetEntry(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    var onDismissRequest by mutableStateOf(onDismissRequest)
    var modifier by mutableStateOf(modifier)
    var title by mutableStateOf(title)
    var content by mutableStateOf(content)
}

/**
 * The one place open sheets live — a stack, not a single slot.
 *
 * Only the topmost is drawn, but a sheet opened *from* a sheet has to find its parent still standing
 * when it closes. Held in one slot, the child overwrote the parent and closing the child left nothing
 * behind: picking a playlist in "Fill from playlist" simply shut both sheets and did nothing.
 */
@Stable
class SheetHostState internal constructor() {
    internal val entries = mutableStateListOf<SheetEntry>()
}

internal val LocalSheetHost = staticCompositionLocalOf<SheetHostState?> { null }

/**
 * The layer every bottom sheet in the app is drawn on, hosted once above the whole UI.
 *
 * A sheet used to be a `ModalBottomSheet`, which is a separate window — and a separate window cannot
 * replay this window's blurred wallpaper, so every long-press menu fell back to a flat, near-opaque
 * fill. Hosting the sheet *inside* the app's own window is what lets it sample the same frost every
 * other pane samples, and it is also what makes a real predictive-back gesture possible.
 */
@Composable
fun MobileSheetHost(content: @Composable () -> Unit) {
    val host = remember { SheetHostState() }
    val entry = host.entries.lastOrNull()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                // Neither a screen reader nor the keyboard's focus may wander into the page behind an
                // open sheet — a dialog window used to give both of those for free, and an in-window
                // sheet has to say so itself.
                .then(
                    if (entry == null) {
                        Modifier
                    } else {
                        Modifier
                            .semantics { hideFromAccessibility() }
                            .focusProperties { canFocus = false }
                    },
                ),
        ) {
            CompositionLocalProvider(LocalSheetHost provides host) { content() }
        }
        // Keyed on the entry, so opening a second sheet builds fresh state rather than sliding the
        // old sheet's position into new content.
        if (entry != null) key(entry) { SheetLayer(entry) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.SheetLayer(entry: SheetEntry) {
    val animations = LocalAnimations.current
    val scope = rememberCoroutineScope()
    val state = remember { AnchoredDraggableState(SheetAnchor.HIDDEN) }

    var containerHeight by remember { mutableStateOf(0) }
    var sheetHeight by remember { mutableStateOf(0) }

    // Anchors are translations downward from the resting position, so the sheet is simply laid out
    // at the bottom of the screen and pushed off it by its own height.
    //
    // Only the anchors are keyed on the measurement. An effect keyed on a size is created before the
    // size is known, so its first run always carries the pre-measurement zeroes and it is always
    // restarted once — which would cancel anything longer-lived started here.
    LaunchedEffect(sheetHeight, containerHeight) {
        if (sheetHeight <= 0 || containerHeight <= 0) return@LaunchedEffect
        val full = sheetHeight.toFloat()
        val half = (containerHeight / 2f).coerceAtMost(full)
        state.updateAnchors(
            DraggableAnchors {
                SheetAnchor.EXPANDED at 0f
                // A sheet shorter than half the screen is already at its half, so it has no middle
                // rest to fall to — two names for one position would only make the drag stutter.
                if (full - half > 1f) SheetAnchor.HALF at full - half
                SheetAnchor.HIDDEN at full
            },
            state.targetValue,
        )
    }

    // Set the moment the opening is over, however it ended. The keyboard effect below waits for it:
    // two `animateTo` calls on one [AnchoredDraggableState] are not two animations but one cancelling
    // the other, and the loser's `runCatching` treats the cancellation as an arrival. The watcher
    // below then armed while the sheet was still settled at HIDDEN and dismissed it on the spot.
    //
    // That is not theoretical. It is the local-sync pairing bug: with the keyboard up — which it
    // always is, the PIN was just typed — the sections sheet replacing the PIN sheet was dismissed in
    // the frame it appeared, `LocalSyncViewModel.cancel()` cleared the step, and the setup wizard
    // read the cleared step as "go back". The pairing had already succeeded on the television; the
    // phone threw the result away and returned to the first screen with nothing to show for it.
    var opened by remember { mutableStateOf(false) }

    // The sheet's whole life, in one coroutine that is never restarted: wait to be measured, come up,
    // and from then on watch for the way down. Coming to rest on HIDDEN is the dismissal however it
    // got there — a fling, a drag, the scrim or the back gesture — so every route cleans up the same
    // way. The watching has to start *after* the opening, because every sheet begins settled at
    // HIDDEN: armed any earlier it reads its own starting position as a close and the menu is gone in
    // the frame it appeared.
    LaunchedEffect(Unit) {
        snapshotFlow { state.anchors.size }.first { it > 0 }
        // A short sheet has no HALF anchor at all, and lands fully open instead.
        val landing = if (state.anchors.positionOf(SheetAnchor.HALF).isNaN()) {
            SheetAnchor.EXPANDED
        } else {
            SheetAnchor.HALF
        }
        // A finger on the sheet while it is still rising cancels the animation. That is not a reason
        // to stop watching for the close.
        runCatching {
            if (animations == AnimationLevel.OFF) state.snapTo(landing) else state.animateTo(landing)
        }
        opened = true
        snapshotFlow { state.settledValue }
            .collect { if (it == SheetAnchor.HIDDEN) entry.onDismissRequest() }
    }

    // The keyboard takes the bottom half of the screen, which is exactly where a half-open sheet
    // rests: the field being typed into ends up behind the keys. Opening the keyboard therefore
    // opens the sheet all the way, and `imePadding` above keeps it clear of the keys.
    //
    // Keyed on [opened] as well, so a sheet born with the keyboard already up — one sheet replacing
    // another mid-flow — lets the opening finish before expanding it, instead of racing it. See
    // [opened] for what that race cost.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, opened) {
        if (!imeVisible || !opened) return@LaunchedEffect
        snapshotFlow { state.anchors.size }.first { it > 0 }
        runCatching {
            if (animations == AnimationLevel.OFF) {
                state.snapTo(SheetAnchor.EXPANDED)
            } else {
                state.animateTo(SheetAnchor.EXPANDED)
            }
        }
    }

    // Going away is the same journey as coming up, and obeys the same setting: with animations off a
    // dismissal is a disappearance, not a quick slide.
    suspend fun close() {
        if (animations == AnimationLevel.OFF) state.snapTo(SheetAnchor.HIDDEN) else state.animateTo(SheetAnchor.HIDDEN)
    }

    // The back gesture shrinks and fades the sheet under the thumb and lets go of it if the gesture
    // completes. Cancelled, it springs back — which is the whole point of the predictive version.
    val backProgress = remember { Animatable(0f) }
    PredictiveBackHandler { events ->
        try {
            events.collect { backProgress.snapTo(it.progress) }
            backProgress.snapTo(0f)
            close()
        } catch (cancelled: CancellationException) {
            if (animations == AnimationLevel.OFF) {
                backProgress.snapTo(0f)
            } else {
                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
            }
            throw cancelled
        }
    }

    val offset = if (state.anchors.size > 0) state.requireOffset() else sheetHeight.toFloat()
    val shown = if (sheetHeight > 0) (1f - offset / sheetHeight).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA * shown * (1f - backProgress.value)))
            // Tapping away is a dismissal, and it is the sheet that animates out rather than the
            // whole layer blinking off. The scrim also swallows the touches the page behind must not
            // receive while a modal sheet is open. It must never take focus: it is a full-screen
            // clickable, so IME Next/Done traversal would otherwise land on it instead of the next
            // field or the keyboard dismiss.
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { scope.launch { close() } },
            ),
    )

    // A sheet stands clear of the screen on all four sides, so all four corners are curved — the
    // wallpaper running round it is what makes the floating material read as floating.
    val settle = remember { Animatable(if (animations == AnimationLevel.OFF) 0f else SETTLE_DP) }
    LaunchedEffect(Unit) {
        if (animations != AnimationLevel.OFF) settle.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
    }
    val body = entry.content

    Column(
        entry.modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = SheetMaxWidth)
            .fillMaxWidth()
            // Measured with its bottom margin included, so "pushed off by its own height" still
            // clears the screen now that the sheet rests above the edge rather than on it.
            .onSizeChanged { sheetHeight = it.height }
            .imePadding()
            // A tall sheet with the keyboard up is the one case that reaches the top of the screen,
            // and its title must not end up printed across the clock.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                start = MobileDimens.ShellInset,
                end = MobileDimens.ShellInset,
                bottom = MobileDimens.ShellInset,
            )
            .offset { IntOffset(0, offset.roundToInt()) }
            .graphicsLayer {
                val shrink = lerp(1f, BACK_SCALE, backProgress.value)
                scaleX = shrink
                scaleY = shrink
                alpha = 1f - backProgress.value * 0.4f
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            // A fling that hands over to the sheet obeys the animation setting the same way a
            // dismissal does: off means it arrives, it does not travel.
            .nestedScroll(
                rememberSheetNestedScroll(
                    state = state,
                    settleSpec = if (animations == AnimationLevel.OFF) {
                        snap()
                    } else {
                        AnchoredDraggableDefaults.SnapAnimationSpec
                    },
                ),
            )
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Vertical,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(state),
            )
            .semantics {
                isTraversalGroup = true
                entry.title?.let { paneTitle = it }
            }
            .glassSurface(GlassSurface.DIALOGS, MobileSheetShape)
            .clip(MobileSheetShape),
    ) {
        // Our own handle, not Material's: theirs pads 22 dp above and below a 4 dp bar, which is a
        // finger's width of empty sheet before the first word.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    top = MobileDimens.GrabPaddingTop,
                    bottom = MobileDimens.GrabPaddingBottom,
                )
                .size(MobileDimens.GrabWidth, MobileDimens.GrabHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GRAB_ALPHA)),
        )
        // The settle rides an inner layer, so what catches up is what is inside the pane — put it on
        // the pane itself and the glass slides off its own bottom edge.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = MobileDimens.SheetPaddingH,
                    end = MobileDimens.SheetPaddingH,
                    bottom = MobileDimens.SheetPaddingBottom,
                )
                .graphicsLayer { translationY = settle.value * density },
            verticalArrangement = Arrangement.spacedBy(MobileDimens.SheetGap),
        ) {
            val column = this
            entry.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = MobileDimens.GapTiny + 2.dp),
                )
            }
            // Everything in a sheet is already standing on a pane of glass, so it draws as the layer
            // behind one — no second frost over the first.
            GlassNest(GlassSurface.DIALOGS) { column.body() }
        }
    }
}

/**
 * Hand the drag between the sheet and the list inside it.
 *
 * Dragging up, the sheet expands before the list starts scrolling; dragging down, the list scrolls
 * until it reaches its own top and only then does the sheet take over. Without this a list inside a
 * sheet swallows every gesture and the sheet can only be moved by its handle.
 *
 * The fling is settled with an animation spec, never with `settle(velocity)`: that overload requires
 * an [AnchoredDraggableState] built with positional and velocity thresholds, and **throws** on one
 * built without them. This state has none, so the velocity overload crashed the app the moment a
 * list inside a sheet was flung rather than dragged.
 */
@Composable
private fun rememberSheetNestedScroll(
    state: AnchoredDraggableState<SheetAnchor>,
    settleSpec: AnimationSpec<Float>,
): NestedScrollConnection = remember(state, settleSpec) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && source == NestedScrollSource.UserInput) {
                Offset(0f, state.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput) {
                Offset(0f, state.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (available.y < 0f && state.requireOffset() > state.anchors.minPosition()) {
                state.settle(settleSpec)
                available
            } else {
                Velocity.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            state.settle(settleSpec)
            return available
        }
    }
}
