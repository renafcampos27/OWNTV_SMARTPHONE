package tv.own.owntv.mobile.ui.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.compose.material3.Text
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.mobile.ui.theme.asComposeFamily
import org.koin.compose.koinInject
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.ZoomMode

/**
 * The picture: the engine's output surface, the zoom the user chose, and whichever subtitle layer the
 * engine is currently using.
 *
 * Used at three sizes without changing — the 16:9 box on the channel screen, the fullscreen player and
 * the docked mini player — because all three are the same stream and the same engine.
 */
@Composable
fun VideoStage(
    player: OwnTVPlayer,
    modifier: Modifier = Modifier,
    subtitleScale: Float = 1f,
    /**
     * Ignore the zoom the user chose and use this instead.
     *
     * The floating window passes FIT. A zoom is a choice about a *screen* — "crop this film to fill my
     * television" — and carrying it into a 176dp window is nonsense: ORIGINAL there means a 1920px
     * frame inside a 460px window, showing a quarter of the picture with no way to see it is cropped.
     */
    zoomOverride: ZoomMode? = null,
    /**
     * Settings → Auto frame rate, seamless switches only (M9, owner decision 7). Passed only by the full
     * screen player, as the setting's description says: the smaller views keep the screen's own rate,
     * and leaving full screen hands it back.
     */
    autoFrameRate: Boolean = false,
) {
    // L2 — which engine holds the picture. Resolved HERE rather than passed in, and that is the whole
    // point: this composable is used at four sizes (the channel screen's panel, full screen, the
    // docked mini player and the floating window) and the first attempt passed the engine to ONE of
    // them. The other three drew mpv's surface while ExoPlayer held the stream, so ExoPlayer had no
    // surface at all — sound, no picture — and the watchdog dutifully handed every channel back to
    // mpv within seconds. A parameter that three of four callers forget is the wrong shape.
    val tuner: LiveTuner = koinInject()
    val liveOnExo by tuner.liveOnExo.collectAsStateWithLifecycle()
    val liveExo = tuner.exoEngine.takeIf { liveOnExo }

    // Shape comes from whichever engine actually holds the video, or the frame is laid out against
    // numbers the idle one last reported.
    val aspect: Float?
    val videoSize: Pair<Int, Int>?
    if (liveExo != null) {
        val a by liveExo.videoAspect.collectAsStateWithLifecycle()
        val size by liveExo.videoSize.collectAsStateWithLifecycle()
        aspect = a
        videoSize = size
    } else {
        val a by player.videoAspect.collectAsStateWithLifecycle()
        val size by player.videoSize.collectAsStateWithLifecycle()
        aspect = a
        videoSize = size
    }
    val chosenZoom by player.zoomMode.collectAsStateWithLifecycle()
    val zoom = zoomOverride ?: chosenZoom

    // Keeping the screen awake is MainActivity's job, not this composable's: three of these exist and
    // they hand the stream to one another, so whichever one was disposed last won.

    BoxWithConstraints(
        modifier.background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val viewModifier = Modifier.videoZoom(zoom, aspect, videoSize, maxWidth, maxHeight)
        // key(surfaceResetToken): when the player bumps the token this whole view is disposed and
        // rebuilt, making a genuinely FRESH Surface. Some decoders only recover on one.
        if (liveExo != null) {
            // Live on ExoPlayer. Its own generation counter replaces mpv's reset token for the same
            // reason mpv has one: some hardware only ever accepts one 4K codec per Surface, so a
            // fresh decoder needs a genuinely fresh Surface, not a reused one.
            // Media3 applies its own seamless-only hint once asked; switched off again on leaving. Only
            // the full-screen stage touches it — a smaller one still composed underneath (the channel
            // screen) must not switch it off in the middle of full screen.
            if (autoFrameRate) {
                androidx.compose.runtime.DisposableEffect(liveExo) {
                    liveExo.setAutoFrameRateEnabled(true)
                    onDispose { liveExo.setAutoFrameRateEnabled(false) }
                }
            }
            val exoGeneration by liveExo.surfaceGeneration.collectAsStateWithLifecycle()
            key(exoGeneration) {
                AndroidView(modifier = viewModifier, factory = { ctx -> ExoSurfaceView(ctx, liveExo) })
            }
            // Live subtitles come out of the same engine, and nothing of mpv's applies here. Mounted
            // only while a subtitle track is on: a view over the SurfaceView costs the overlay path.
            val subtitleOn by liveExo.subtitleOn.collectAsStateWithLifecycle()
            if (subtitleOn) {
                val cues by liveExo.cues.collectAsStateWithLifecycle()
                StyledSubtitleView(cues = cues, modifier = viewModifier)
            }
            return@BoxWithConstraints
        }
        val surfaceResetToken by player.surfaceResetToken.collectAsStateWithLifecycle()
        // mpv's measured frame rate (it only measures while the setting is on).
        val fps by player.videoFps.collectAsStateWithLifecycle()
        key(surfaceResetToken) {
            AndroidView(
                modifier = viewModifier,
                factory = { ctx -> MpvSurfaceView(ctx, player) },
                update = { it.applyVideoFrameRate(if (autoFrameRate) fps ?: 0f else 0f) },
            )
        }
        // Image subtitles (PGS/VOBSUB/DVB) come through ExoPlayer. Mounted ONLY while ExoPlayer owns
        // playback: putting any view over the SurfaceView knocks it off the hardware-overlay path,
        // which turns 4K into a slideshow. During plain mpv playback this is not composed at all.
        // And only while a subtitle track is actually on, as the live branch above already does: a film
        // on ExoPlayer with subtitles off would otherwise keep an empty view over the surface.
        val exoActive by player.exoActiveState.collectAsStateWithLifecycle()
        val exoSubOn by player.exoSubtitleOn.collectAsStateWithLifecycle()
        if (exoActive && exoSubOn) {
            val cues by player.exoCues.collectAsStateWithLifecycle()
            StyledSubtitleView(cues = cues, modifier = viewModifier)
        }
        // The last mpv frame, held over the surface during the mpv→ExoPlayer swap so the decoder
        // change does not flash black. Cleared on ExoPlayer's first frame.
        val freeze by player.freezeFrame.collectAsStateWithLifecycle()
        freeze?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = viewModifier,
                contentScale = ContentScale.FillBounds,
            )
        }
        if (!exoActive) SubtitleOverlay(player = player, modifier = viewModifier, sizeScale = subtitleScale)
    }
}

/**
 * A plain `SurfaceView` handed to the live ExoPlayer engine — the same thing a Multiview tile gives
 * its own engine, which is why it is this short: the engine does the rest.
 */
private class ExoSurfaceView(context: Context, engine: LivePreviewEngine) :
    android.view.SurfaceView(context) {
    init {
        holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) = engine.setSurface(holder.surface)
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) = engine.detachSurface(holder.surface)
        })
    }
}

private class MpvSurfaceView(context: Context, private val player: OwnTVPlayer) :
    SurfaceView(context), SurfaceHolder.Callback {

    private var pendingFps = 0f

    init {
        holder.addCallback(this)
    }

    /** Seamless-only frame-rate hint (M9); re-applied on each fps change and on a surface re-create. */
    fun applyVideoFrameRate(fps: Float) {
        pendingFps = fps
        val surface = holder.surface ?: return
        tv.own.owntv.player.SurfaceFrameRate.apply(surface, fps, seamlessOnly = true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        player.attachSurface(holder.surface)
        if (pendingFps > 0f) applyVideoFrameRate(pendingFps)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
        player.setSurfaceSize(width, height)

    // This app has three views of one stream, and the outgoing one is destroyed AFTER the incoming
    // one has attached — so the detach names its own surface and does nothing once it is stale.
    override fun surfaceDestroyed(holder: SurfaceHolder) = player.detachSurface(holder.surface)
}

private const val ZOOM_CROP_FACTOR = 1.2f

/**
 * Zoom / aspect, done by **sizing the surface** inside a clipped black box rather than by asking the
 * engine to scale — the engine always fills its surface edge to edge. The same math as the TV app's,
 * so a channel zoomed on the phone and on the television look alike.
 *
 * Must be called inside a [BoxWithConstraints] so the container's [maxWidth]/[maxHeight] can be passed.
 */
@Composable
private fun Modifier.videoZoom(
    zoom: ZoomMode,
    aspect: Float?,
    videoSize: Pair<Int, Int>?,
    maxWidth: Dp,
    maxHeight: Dp,
): Modifier {
    if (aspect == null || aspect <= 0f) return fillMaxSize() // no dimensions yet
    val containerAspect = maxWidth.value / maxHeight.value
    return when (zoom) {
        ZoomMode.STRETCH -> fillMaxSize()
        ZoomMode.ORIGINAL -> {
            val vs = videoSize
            if (vs != null && vs.first > 0 && vs.second > 0) {
                // requiredSize, not size: a 4K frame on a smaller screen must be allowed to overflow
                // the container, and plain size() is clamped back to the incoming constraint.
                with(LocalDensity.current) { requiredSize(vs.first.toDp(), vs.second.toDp()) }
            } else {
                aspectRatio(aspect)
            }
        }
        ZoomMode.FILL -> {
            // Take the letterboxed fit box and scale it past the container on every side; the parent
            // clips the excess. Visible even when the video's aspect already matches the screen's.
            val fitWidthDriven = aspect >= containerAspect
            val fitW = if (fitWidthDriven) maxWidth.value else maxHeight.value * aspect
            val fitH = if (fitWidthDriven) maxWidth.value / aspect else maxHeight.value
            requiredWidth((fitW * ZOOM_CROP_FACTOR).dp).requiredHeight((fitH * ZOOM_CROP_FACTOR).dp)
        }
        else -> {
            val targetAspect = when (zoom) {
                ZoomMode.FORCE_16_9 -> 16f / 9f
                ZoomMode.FORCE_4_3 -> 4f / 3f
                else -> aspect // FIT keeps the video's own shape
            }
            if (targetAspect >= containerAspect) {
                width(maxWidth).height((maxWidth.value / targetAspect).dp)
            } else {
                height(maxHeight).width((maxHeight.value * targetAspect).dp)
            }
        }
    }
}

/**
 * Text subtitles on the direct-render path: the decoder owns the surface there, so mpv cannot draw
 * its own, and the player publishes the active line for the app to render instead.
 */
@Composable
private fun SubtitleOverlay(player: OwnTVPlayer, modifier: Modifier = Modifier, sizeScale: Float = 1f) {
    val text by player.subText.collectAsStateWithLifecycle()
    val line = text ?: return
    val settings = koinInject<SettingsRepository>()
    val styleOn by settings.subtitleStyleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scale by settings.subtitleScaleMpv.collectAsStateWithLifecycle(initialValue = SubtitleStyle.SCALE_DEFAULT)
    val font by settings.subtitleFont.collectAsStateWithLifecycle(initialValue = null)
    val colorHex by settings.subtitleColor.collectAsStateWithLifecycle(initialValue = SubtitleStyle.COLOR_DEFAULT)
    val position by settings.subtitlePosition
        .collectAsStateWithLifecycle(initialValue = SubtitleStyle.Position.DEFAULT)
    val bgOpacity by settings.subtitleBgOpacity
        .collectAsStateWithLifecycle(initialValue = SubtitleStyle.OPACITY_DEFAULT)

    val textScale = if (styleOn) scale else SubtitleStyle.SCALE_DEFAULT
    val textColor =
        if (styleOn && SubtitleStyle.hasColor(colorHex)) Color(SubtitleStyle.colorArgb(colorHex)) else Color.White
    val boxColor = if (styleOn && SubtitleStyle.hasOpacity(bgOpacity)) {
        Color(SubtitleStyle.backgroundArgb(bgOpacity))
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    val anchor = if (styleOn) position else SubtitleStyle.Position.DEFAULT

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = 24.dp * sizeScale,
                vertical = (if (anchor.isTop) 24.dp else 32.dp) * sizeScale,
            ),
        contentAlignment = anchor.alignment(),
    ) {
        Text(
            text = line,
            textAlign = anchor.textAlign(),
            style = TextStyle(
                color = textColor,
                fontSize = (18 * textScale * sizeScale).sp,
                lineHeight = (23 * textScale * sizeScale).sp,
                fontFamily = if (styleOn) font?.asComposeFamily() ?: FontFamily.SansSerif else FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 6f),
            ),
            modifier = Modifier
                .widthIn(max = 600.dp * sizeScale)
                .clip(RoundedCornerShape(8.dp))
                .background(boxColor)
                .padding(horizontal = 12.dp * sizeScale, vertical = 4.dp * sizeScale),
        )
    }
}

/** Media3's own subtitle view, with the user's appearance settings applied, for image subtitles. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StyledSubtitleView(cues: List<Cue>, modifier: Modifier = Modifier) {
    val settings = koinInject<SettingsRepository>()
    val styleOn by settings.subtitleStyleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scale by settings.subtitleScaleExo.collectAsStateWithLifecycle(initialValue = SubtitleStyle.SCALE_DEFAULT)
    val colorHex by settings.subtitleColor.collectAsStateWithLifecycle(initialValue = SubtitleStyle.COLOR_DEFAULT)
    val bgOpacity by settings.subtitleBgOpacity
        .collectAsStateWithLifecycle(initialValue = SubtitleStyle.OPACITY_DEFAULT)

    val customColor = styleOn && SubtitleStyle.hasColor(colorHex)
    val customBackground = styleOn && SubtitleStyle.hasOpacity(bgOpacity)
    val textScale = if (styleOn) scale else SubtitleStyle.SCALE_DEFAULT

    AndroidView(
        modifier = modifier,
        factory = { ctx -> SubtitleView(ctx) },
        update = { view ->
            val stock = CaptionStyleCompat.DEFAULT
            if (customColor || customBackground) {
                view.setApplyEmbeddedStyles(false)
                view.setStyle(
                    CaptionStyleCompat(
                        if (customColor) SubtitleStyle.colorArgb(colorHex) else stock.foregroundColor,
                        if (customBackground) SubtitleStyle.backgroundArgb(bgOpacity) else stock.backgroundColor,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null,
                    ),
                )
            } else {
                view.setApplyEmbeddedStyles(true)
                view.setStyle(stock)
            }
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * textScale)
            view.setCues(cues)
        },
    )
}

private fun SubtitleStyle.Position.alignment(): Alignment = when {
    isTop && isLeft -> Alignment.TopStart
    isTop && isRight -> Alignment.TopEnd
    isTop -> Alignment.TopCenter
    isLeft -> Alignment.BottomStart
    isRight -> Alignment.BottomEnd
    else -> Alignment.BottomCenter
}

private fun SubtitleStyle.Position.textAlign(): TextAlign = when {
    isLeft -> TextAlign.Start
    isRight -> TextAlign.End
    else -> TextAlign.Center
}
