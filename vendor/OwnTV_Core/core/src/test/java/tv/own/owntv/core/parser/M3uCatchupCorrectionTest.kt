package tv.own.owntv.core.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class M3uCatchupCorrectionTest {
    @Test fun headerCorrectionIsInheritedAndEntryCanOverrideOrClearIt() = runBlocking {
        val output = mutableListOf<M3uEntry>()
        M3uParser().parse("""
            #EXTM3U catchup-correction="1.5"
            #EXTINF:-1 catchup="append",Inherited
            https://example.invalid/1
            #EXTINF:-1 catchup="append" catchup-correction="-1.25",Overridden
            https://example.invalid/2
            #EXTINF:-1 catchup="append" catchup-correction="0",Cleared
            https://example.invalid/3
            #EXTINF:-1 catchup="append" catchup-correction="NaN",Invalid
            https://example.invalid/4
        """.trimIndent().byteInputStream()) { output += it }
        assertEquals(listOf(90, -75, 0, 90), output.map { it.catchupCorrectionMin })
    }
    @Test fun missingOrOverflowingCorrectionsDoNotAlterOrdinaryChannels() = runBlocking {
        val output = mutableListOf<M3uEntry>()
        M3uParser().parse("""
            #EXTM3U catchup-correction="1e99"
            #EXTINF:-1,Normal
            https://example.invalid/normal
        """.trimIndent().byteInputStream()) { output += it }
        assertEquals(0, output.single().catchupCorrectionMin)
    }
}
