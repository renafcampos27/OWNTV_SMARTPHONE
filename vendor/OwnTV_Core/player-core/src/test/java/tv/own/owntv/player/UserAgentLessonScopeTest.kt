package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class UserAgentLessonScopeTest {
    @Test fun `confirmed identity lesson does not contaminate a sibling channel`() {
        LiveStreamQuirks.clearForTest()
        LiveStreamQuirks.rememberBlocksDefaultUserAgent("https://same.test/channel-a", 100)
        assertTrue(LiveStreamQuirks.blocksDefaultUserAgent("https://same.test/channel-a", 101))
        assertFalse(LiveStreamQuirks.blocksDefaultUserAgent("https://same.test/channel-b", 101))
    }
    @Test fun `lesson expires and a later explicit success can relearn it`() {
        LiveStreamQuirks.clearForTest()
        val url = "https://same.test/channel"
        LiveStreamQuirks.rememberBlocksDefaultUserAgent(url, 100)
        assertFalse(LiveStreamQuirks.blocksDefaultUserAgent(url, 100 + 30 * 60_000))
        LiveStreamQuirks.rememberBlocksDefaultUserAgent(url, 100 + 30 * 60_000)
        assertTrue(LiveStreamQuirks.blocksDefaultUserAgent(url, 101 + 30 * 60_000))
    }
}
