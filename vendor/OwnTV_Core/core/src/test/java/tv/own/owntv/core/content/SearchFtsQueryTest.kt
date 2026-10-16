package tv.own.owntv.core.content
import org.junit.Assert.*
import org.junit.Test
class SearchFtsQueryTest {
    @Test fun punctuationSeparatesWordsWithoutJoiningThem() {
        assertEquals("Spider* M*", SearchReader.ftsQuery("Spider-M"))
        assertEquals("PT* RTP* 1* HD*", SearchReader.ftsQuery("PT: RTP-1/HD"))
        assertEquals("Coração* ação*", SearchReader.ftsQuery("Coração & ação"))
    }
    @Test fun symbolsOnlyAndWhitespaceHaveNoFtsExpression() {
        assertNull(SearchReader.ftsQuery("--- / &"))
        assertNull(SearchReader.ftsQuery("  "))
        assertEquals("RTP* 1*", SearchReader.ftsQuery(" RTP  1 "))
    }
}
