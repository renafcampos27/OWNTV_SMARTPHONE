package tv.own.owntv.core.parser

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class XmltvPrecision68Test {
    @Test fun minutePrecisionSecondsColonOffsetAndZuluResolveToTheSameInstant() {
        val expected = Instant.parse("2026-10-18T12:15:00Z").toEpochMilli()
        for (value in listOf("202610181215", "20261018121500", "202610181215Z", "20261018121500Z",
            "202610181315 +0100", "20261018131500 +01:00", "202610181045 -0130")) {
            assertEquals(value, expected, parseXmltvTime(value))
        }
    }
    @Test fun malformedPrecisionAndOffsetsStillReturnZeroWithoutThrowing() {
        for (value in listOf(null, "", "20261018", "202610181215x", "202610181215 +0", "202610181215 +ab:cd")) {
            assertEquals(value, 0L, parseXmltvTime(value))
        }
    }
}
