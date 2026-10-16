package tv.own.owntv.core.content

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeSearchPolicyTest {
    @Test fun literalWildcardsDoNotExpandSearch() {
        assertEquals("%a\\%b\\_c\\\\d%", ProgrammeSearchPolicy.literalPattern(" a%b_c\\d "))
    }
    @Test fun unicodeTitleIsPreserved() {
        assertEquals("%Notícias%", ProgrammeSearchPolicy.literalPattern(" Notícias "))
    }
}
