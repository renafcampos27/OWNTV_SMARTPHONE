package tv.own.owntv.mobile.ui.screens.multiview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.live.StreamGrant
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.live.connectionBudget
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.player.LiveEnginePool

/** One tile of the grid: empty, playing a channel, or explaining why it could not start. */
/**
 * How many tiles a grid opens with: two, or the ceiling when the user set it lower.
 *
 * The television's rule, for the same reason — Multiview is for watching more than one thing, and
 * opening at one would be indistinguishable from full screen. The second tile is the invitation.
 */
private fun openingTileCount(maxTiles: Int): Int = minOf(2, maxTiles).coerceAtLeast(1)

data class MobileMultiviewTile(
    val channel: ChannelEntity? = null,
    val refusal: StreamGrant.Refused? = null,
    /**
     * The *device* could not carry this tile: the provider had already allowed it and the picture
     * stopped anyway. Several simultaneous 4K streams is the real-world case.
     */
    val deviceLimit: Boolean = false,
) {
    val isEmpty: Boolean get() = channel == null && refusal == null && !deviceLimit
}

/**
 * What the phone's Multiview grid is showing, and the rules for changing it.
 *
 * The television's `MultiviewState` in every respect that matters — same core budget, same registry,
 * same one-tile-has-the-sound rule — and separate only because looking up a channel's playlist is a
 * suspending call on this side. Two screens, one set of rules, both of them core's.
 */
@UnstableApi
class MobileMultiviewState(
    val pool: LiveEnginePool,
    private val registry: OpenStreamRegistry,
    private val tuner: LiveTuner,
    private val scope: CoroutineScope,
    /**
     * The **most** tiles this grid may have, from Settings — not how many it opens with. The setting
     * is a ceiling: how many to actually watch is decided in front of the grid, every time.
     */
    private val maxTiles: Int,
) {
    /**
     * The tiles on screen now. Grows and shrinks from the end only, which keeps a tile's index — and
     * so its engine in [LiveEnginePool] — the same for as long as that tile exists.
     */
    val tiles = mutableStateListOf<MobileMultiviewTile>().apply {
        repeat(openingTileCount(maxTiles)) { add(MobileMultiviewTile()) }
    }

    /** Room for one more? */
    val canAddTile: Boolean get() = tiles.size < maxTiles

    /** One more empty tile for the user to fill. */
    fun addTile() {
        if (canAddTile) tiles.add(MobileMultiviewTile())
    }

    /**
     * A tile that was playing has stopped while the rest of the grid carries on.
     *
     * The provider was already asked and allowed it, so this is the phone reaching its own limit —
     * decoders, bandwidth or video planes. It deliberately does not blame the provider: the
     * television had a rule that did, and it was wrong every time it fired.
     */
    fun refuseDeviceLimit(tile: Int) {
        if (tiles.getOrNull(tile)?.channel == null) return
        releaseClaim(tile)
        pool.release(tile)
        soundOnly.remove(tile)
        tiles[tile] = MobileMultiviewTile(deviceLimit = true)
        if (audible == tile) {
            val next = tiles.indexOfFirst { it.channel != null }
            if (next >= 0) giveSoundTo(next) else pool.giveSoundTo(null)
        }
    }

    var audible by mutableIntStateOf(0)
        private set

    private val claims = HashMap<Int, OpenStreamRegistry.Claim>()

    /**
     * Tiles playing sound with no picture — the owner's background-audio case (§2.4): watch one
     * channel while another's commentary plays behind it. It still costs a provider connection,
     * because dropping the video track does not close the stream, so the tile says so.
     */
    val soundOnly = mutableStateListOf<Int>()

    fun setSoundOnly(tile: Int, value: Boolean) {
        if (tiles.getOrNull(tile)?.channel == null) return
        pool.setSoundOnly(tile, value)
        if (value) {
            if (tile !in soundOnly) soundOnly.add(tile)
            audible = tile
        } else {
            soundOnly.remove(tile)
        }
    }

    /**
     * A tile whose engine has failed keeps its audio track playing — the picture is what failed, not
     * the stream — so a tile reading "Couldn't play this channel" was still making a noise. Silence
     * it, and move the sound to a tile that is actually showing something.
     */
    fun silenceFailed(tile: Int) {
        pool.peek(tile)?.setMuted(true)
        if (audible != tile) return
        val next = tiles.indices.firstOrNull { it != tile && tiles[it].channel != null && !failed(it) }
        if (next != null) giveSoundTo(next) else pool.giveSoundTo(null)
    }

    private fun failed(tile: Int): Boolean =
        pool.peek(tile)?.state?.value == tv.own.owntv.player.LivePreviewEngine.State.ERROR

    fun giveSoundTo(tile: Int) {
        // A failed tile has no sound to give, so handing it the sound would only un-mute the audio of
        // a channel that is not on screen.
        if (failed(tile)) return
        if (tiles.getOrNull(tile)?.channel == null) return
        audible = tile
        pool.giveSoundTo(tile)
    }

    /** Put [channel] in [tile] if its playlist has a connection to spare, else show the reason (D11). */
    fun fill(tile: Int, channel: ChannelEntity) {
        if (tile !in tiles.indices) return
        // **Which tile gets the sound is decided here, synchronously, and not inside the coroutine.**
        //
        // Filling four tiles launches four coroutines that all suspend on the playlist lookup before
        // any of them has written its tile. Asked inside the coroutine, every one of them saw an
        // empty grid, so every one of them claimed the sound: each tuned its engine unmuted, and each
        // `giveSoundTo` muted engines that a later `play(muted = false)` promptly un-muted again.
        // Two channels audible at once — the phone's last Multiview defect, and invisible to every
        // fix aimed at the engines, because the engines were doing exactly what they were told.
        //
        // The television never had it: its own `fill` is synchronous throughout.
        val takesSound = tiles.none { it.channel != null }
        tiles[tile] = MobileMultiviewTile(channel = channel)
        if (takesSound) audible = tile
        scope.launch {
            releaseClaim(tile)
            val source = tuner.sourceOf(channel)
            val grant = connectionBudget(source, registry.openOn(channel.sourceId), StreamPurpose.WATCHING)
            if (grant is StreamGrant.Refused) {
                tiles[tile] = MobileMultiviewTile(refusal = grant)
                // It was going to be the audible one; it is not going to be anything.
                if (audible == tile) {
                    val next = tiles.indexOfFirst { it.channel != null }
                    if (next >= 0) giveSoundTo(next) else pool.giveSoundTo(null)
                }
                return@launch
            }
            claims[tile] = registry.claim(channel.sourceId, StreamPurpose.WATCHING)
            // One tile is unmuted, every other is muted, and both facts were settled before any of
            // these coroutines started.
            tuner.tuneTile(pool.engineFor(tile), channel, muted = audible != tile)
            if (audible == tile) pool.giveSoundTo(tile)
        }
    }

    fun clear(tile: Int) {
        if (tile !in tiles.indices) return
        releaseClaim(tile)
        pool.release(tile)
        soundOnly.remove(tile)
        tiles[tile] = MobileMultiviewTile()
        // Emptying the last tile removes it, so "Remove tile" shrinks a grid the user has finished
        // with. Only ever from the end, so no surviving tile changes index and no engine is handed
        // to a different channel.
        while (tiles.size > openingTileCount(maxTiles) && tiles.last().isEmpty) {
            pool.release(tiles.lastIndex)
            tiles.removeAt(tiles.lastIndex)
        }
        if (audible > tiles.lastIndex) audible = tiles.lastIndex.coerceAtLeast(0)
        if (audible == tile) audible = tiles.indexOfFirst { it.channel != null }.coerceAtLeast(0)
    }

    /** Leaving the grid: every engine and every claim goes, or the next tile is refused for a ghost. */
    fun releaseAll() {
        claims.values.forEach { registry.release(it) }
        claims.clear()
        pool.releaseAll()
    }

    private fun releaseClaim(tile: Int) {
        claims.remove(tile)?.let { registry.release(it) }
    }
}
