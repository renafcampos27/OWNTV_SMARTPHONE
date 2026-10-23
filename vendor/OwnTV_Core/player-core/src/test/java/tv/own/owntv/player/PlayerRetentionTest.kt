package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PlayerRetentionTest {
    private fun track(index: Int, lang: String?, label: String = "") = TrackOption(label, index, false, lang = lang, typeIndex = index)

    @Test fun aliasesSurviveReorderingWithoutChoosingCommentary() {
        val retained = track(0, "eng", "Main")
        val actual = listOf(track(0, "fr", "French"), track(2, "en", "Main"), track(3, "en", "Commentary"))
        assertEquals(2, AudioTrackRetention.match(retained, actual)?.typeIndex)
    }
    @Test fun missingOrAmbiguousIdentityIsNotReplacedArbitrarily() {
        assertNull(AudioTrackRetention.match(track(0, "en"), listOf(track(1, "en"), track(2, "eng"))))
        assertNull(AudioTrackRetention.match(track(0, "en"), listOf(track(0, "fr"))))
        assertNull(AudioTrackRetention.match(track(-1, null), listOf(track(0, null))))
    }
    @Test fun explicitRegionsAndScriptsRemainDistinct() {
        assertTrue(explicitTrackLanguageMatches("eng", "en-US"))
        assertTrue(explicitTrackLanguageMatches("pt_PT", "pt-PT"))
        assertFalse(explicitTrackLanguageMatches("pt-PT", "pt-BR"))
        assertFalse(explicitTrackLanguageMatches("zh-Hans", "zh-Hant"))
    }
    @Test fun archiveRetryCapturesIndependentContextAndLiveNeverInheritsIt() {
        val alternatives = mutableListOf("alternate")
        val types = mutableMapOf("alternate" to "hls")
        val item = ItemState(archiveThisItem = true, archiveAlternatives = alternatives, archiveManifestTypes = types)
        val captured = ArchiveRetryContext.capture(item, false)
        alternatives.clear(); types.clear()
        assertTrue(captured.isArchive)
        assertEquals(listOf("alternate"), captured.alternatives)
        assertEquals(mapOf("alternate" to "hls"), captured.manifestTypes)
        assertFalse(ArchiveRetryContext.capture(item, true).isArchive)
        assertTrue(ArchiveRetryContext.capture(item, true).alternatives.isEmpty())
        assertTrue(ArchiveRetryContext.capture(ItemState(), false).manifestTypes.isEmpty())
    }
}
