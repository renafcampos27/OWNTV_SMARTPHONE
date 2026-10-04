package tv.own.owntv.mobile.ui.screens.live

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.player.OwnTVPlayer

/**
 * One channel, watched.
 *
 * The phone has no room for the TV app's three panels, so this screen *is* the player plus the three
 * things the panels held: what is on now, the rest of the guide, and the other channels. Every one of
 * them comes from [LiveTuner], which outlives this screen — leaving it for the mini player, or going
 * fullscreen, must not interrupt the stream.
 */
class ChannelDetailViewModel(private val tuner: LiveTuner) : ViewModel() {

    val player: OwnTVPlayer get() = tuner.player
    val channel: StateFlow<ChannelEntity?> = tuner.channel
    val nowNext: StateFlow<EpgNowNext?> = tuner.nowNext
    val siblings: StateFlow<List<ChannelEntity>> = tuner.siblings

    fun load(channelId: Long) = tuner.tune(channelId)

    fun switchTo(channel: ChannelEntity) = tuner.switchTo(channel)

    suspend fun catchupProgrammes(): List<EpgProgrammeEntity> = tuner.catchupProgrammes()

    fun playCatchup(programme: EpgProgrammeEntity) = tuner.playCatchup(programme)
}
