package tv.own.owntv.mobile.ui.screens.live

internal enum class ChannelPressAction { PLAY, VERSIONS, MENU }
internal fun channelPressAction(durationMs: Long): ChannelPressAction = when {
    durationMs >= 3_000L -> ChannelPressAction.MENU
    durationMs >= 2_000L -> ChannelPressAction.VERSIONS
    else -> ChannelPressAction.PLAY
}
