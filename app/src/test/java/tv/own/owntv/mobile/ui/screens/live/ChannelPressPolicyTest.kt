package tv.own.owntv.mobile.ui.screens.live
import org.junit.Assert.assertEquals
import org.junit.Test
class ChannelPressPolicyTest {
    @Test fun thresholdBoundariesNeverOverlap() {
        assertEquals(ChannelPressAction.PLAY, channelPressAction(1_999))
        assertEquals(ChannelPressAction.VERSIONS, channelPressAction(2_000))
        assertEquals(ChannelPressAction.VERSIONS, channelPressAction(2_999))
        assertEquals(ChannelPressAction.MENU, channelPressAction(3_000))
    }
    @Test fun shortClicksAndLongHoldsRemainDeterministic() {
        assertEquals(ChannelPressAction.PLAY, channelPressAction(0))
        assertEquals(ChannelPressAction.MENU, channelPressAction(60_000))
    }
}
