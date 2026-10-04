package tv.own.owntv.mobile.ui.screens.multiview

import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.displayText
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.PlaybackFailure
import tv.own.owntv.player.describe

/**
 * Multiview for a finger: the same grid as the television, driven by taps.
 *
 * | Gesture | Does |
 * | --- | --- |
 * | tap a filled tile | give it the sound |
 * | tap an empty tile | open the channel picker |
 * | double-tap | that tile goes fullscreen |
 * | long-press | the tile menu |
 * | Back | leave, stopping every tile |
 *
 * Landscape is the real mode — that is where four 16:9 pictures fit — and portrait stacks them, which
 * is honest rather than clever: a 2×2 grid on a portrait phone is four postage stamps.
 */
@UnstableApi
@Composable
fun MultiviewScreen(
    state: MobileMultiviewState,
    onPickChannel: (tile: Int) -> Unit,
    onFullscreen: (ChannelEntity) -> Unit,
    /** Back. Takes no channel: leaving the grid now stops playback rather than promoting a tile. */
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = menuFor == null) { onExit() }

    /*
     * Leaving the app closes the grid, exactly as Back does.
     *
     * Four tiles are four decoders and four of the playlist's connections. Nothing stopped them when
     * the app went off screen: pressing Home left all four streaming into a window nobody could see,
     * draining the battery and holding connections that a recording — or the television in the next
     * room — then could not have.
     *
     * `ON_STOP`, not `ON_PAUSE`: a notification shade or a permission dialog is not leaving. A
     * rotation does not reach here at all, because the activity declares `configChanges` for it and
     * is never recreated. Picture-in-Picture cannot reach here either — it needs a playing engine,
     * and opening the grid stopped both of the single-stream ones.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onExit()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Held while the grid is up. The activity's keep-screen-on follows only the single-stream
    // engines, which opening the grid stops — so the phone's own timeout turned the screen off, and
    // the ON_STOP above then closed the grid in the middle of the match.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // Landscape: two per row. Portrait: one per row, stacked.
        val rows = if (landscape) state.tiles.indices.chunked(2) else state.tiles.indices.map { listOf(it) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { index ->
                        Tile(
                            state = state,
                            index = index,
                            onPickChannel = { onPickChannel(index) },
                            onFullscreen = { state.tiles[index].channel?.let(onFullscreen) },
                            onMenu = { menuFor = index },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    if (row.size == 1 && landscape) Box(Modifier.weight(1f))
                }
            }
        }
    }

    menuFor?.let { tile ->
        MultiviewTileSheet(
            filled = state.tiles[tile].channel != null,
            soundOnly = tile in state.soundOnly,
            // Growing the grid is a deliberate act, which is what makes the Settings number a
            // ceiling rather than a size: four allowed does not mean four every time.
            onAddTile = if (state.canAddTile) { { menuFor = null; state.addTile() } } else null,
            onChangeChannel = { menuFor = null; onPickChannel(tile) },
            onFullscreen = { menuFor = null; state.tiles[tile].channel?.let(onFullscreen) },
            onSound = { menuFor = null; state.giveSoundTo(tile) },
            onSoundOnly = { menuFor = null; state.setSoundOnly(tile, tile !in state.soundOnly) },
            onRemove = { menuFor = null; state.clear(tile) },
            onDismiss = { menuFor = null },
        )
    }
}

@UnstableApi
@Composable
private fun Tile(
    state: MobileMultiviewState,
    index: Int,
    onPickChannel: () -> Unit,
    onFullscreen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tile = state.tiles[index]
    val audible = state.audible == index
    Box(
        modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = if (audible) 2.dp else 1.dp,
                color = if (audible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = TileShape,
            )
            .pointerInput(index, tile.channel?.id) {
                detectTapGestures(
                    onTap = { if (tile.channel == null) onPickChannel() else state.giveSoundTo(index) },
                    onDoubleTap = { if (tile.channel != null) onFullscreen() },
                    onLongPress = { onMenu() },
                )
            },
    ) {
        val channel = tile.channel
        val refusal = tile.refusal
        when {
            channel != null -> {
                // A sound-only tile has no picture by design, so it says what it is rather than
                // showing the black rectangle that would otherwise read as a fault.
                if (index in state.soundOnly) {
                    Text(
                        text = stringResource(R.string.multiview_sound_only_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    )
                } else {
                    val engine = state.pool.engineFor(index)
                    val engineState by engine.state.collectAsStateWithLifecycle()
                    val failure by engine.error.collectAsStateWithLifecycle()
                    // A tile whose picture stops while the rest of the grid plays on. The provider
                    // was already asked and said yes, so this is the phone running out of what it
                    // has — said in seconds, rather than the eighty the engine's own reconnect
                    // ladder spends going black before it admits anything.
                    LaunchedEffect(index, channel.id) {
                        engine.isPlaying.collectLatest { playing ->
                            if (playing) return@collectLatest
                            delay(StreamLostGraceMs)
                            // An engine that has diagnosed itself is left alone: its own words are
                            // more specific than anything guessed here.
                            if (engine.error.value != null) return@collectLatest
                            if (!engine.isPlaying.value) state.refuseDeviceLimit(index)
                        }
                    }
                    // The picture failing does not stop the audio track, so a tile showing its
                    // failure went on making a noise from a channel nobody could see.
                    LaunchedEffect(engineState, index) {
                        if (engineState == LivePreviewEngine.State.ERROR) state.silenceFailed(index)
                    }
                    if (engineState == LivePreviewEngine.State.ERROR) {
                        // Never a blank rectangle: with no explanation it is indistinguishable from
                        // the app being broken.
                        Text(
                            text = tileFailureText(failure),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        )
                    } else {
                        TileSurface(engine = engine, modifier = Modifier.fillMaxSize())
                    }
                }
                TileCaption(name = channel.name, audible = audible)
            }
            // The phone, not the provider: it had already allowed this stream.
            tile.deviceLimit -> Text(
                text = stringResource(R.string.multiview_decoder_exhausted_mobile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
            refusal != null -> Text(
                text = refusal.displayText(LocalContext.current.resources),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
            else -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = MobileIcons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = stringResource(R.string.multiview_add_channel),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One tile's picture, drawn through a [TextureView].
 *
 * **Not a SurfaceView.** A SurfaceView is a hole punched in the window that the display hardware
 * fills directly — right for one full-screen picture, and the reason full screen still uses one —
 * but a device has only so many hardware video planes, and several have exactly one. A second
 * SurfaceView asking for a plane that does not exist gets no picture at all, while its audio, which
 * needs no plane, carries on. That is what the television did before this change: one tile with
 * sound and no image, and a decoder log full of dropped frames and no error. A TextureView is an
 * ordinary GPU-composited view, so any number of them can draw at once.
 *
 * Keyed on the engine's surface generation for the same reason the television's is: releasing a 4K
 * decoder bumps that counter, and some hardware only ever accepts one 4K codec per Surface.
 */
@UnstableApi
@Composable
private fun TileSurface(engine: LivePreviewEngine, modifier: Modifier = Modifier) {
    val surfaceGeneration by engine.surfaceGeneration.collectAsStateWithLifecycle()
    Box(modifier.background(Color.Black)) {
        key(surfaceGeneration) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        // The Surface is ours to make and ours to release: a TextureView hands out a
                        // SurfaceTexture, not a Surface, and leaking one keeps a decoder alive.
                        var surface: Surface? = null
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                surface = Surface(texture).also { engine.setSurface(it) }
                            }

                            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                surface?.let { engine.detachSurface(it); it.release() }
                                surface = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TileCaption(name: String, audible: Boolean) {
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (audible) {
            Icon(
                imageVector = MobileIcons.VolumeUp,
                contentDescription = stringResource(R.string.multiview_audio_tile),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What a failed tile says: the engine's own diagnosis, and a plain fallback when it has none.
 *
 * It never invents a number of provider connections. The television had a version that did, reading
 * a stopped tile as proof of the provider's limit, and it was wrong every time — including telling
 * the owner his provider allowed three streams while three of them played beside the message.
 */
@Composable
private fun tileFailureText(failure: PlaybackFailure?): String = when {
    failure == PlaybackFailure.DecoderExhausted -> stringResource(R.string.multiview_decoder_exhausted_mobile)
    failure != null -> {
        LocalConfiguration.current // so a language change recomposes this, as stringResource would
        val resources = LocalContext.current.resources
        failure.describe { id, args -> resources.getString(id, *args.toTypedArray()) }
    }
    else -> stringResource(R.string.player_error_channel)
}

/**
 * How long a tile may be stopped before the grid calls it lost.
 *
 * Comfortably longer than the slowest tile open measured on real hardware, so a channel that is
 * merely slow to start is never mistaken for one the device could not carry — and far shorter than
 * the engine's eight-attempt ladder, which takes over eighty seconds to conclude anything.
 */
private const val StreamLostGraceMs = 12_000L

private val TileGap = 4.dp
private val TileShape = RoundedCornerShape(10.dp)
