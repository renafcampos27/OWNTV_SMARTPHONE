package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleShiftTest {

    @Test
    fun `srt standard timestamps are shifted correctly`() {
        val srt = """
            1
            00:00:20,000 --> 00:00:24,400
            Hello world!
        """.trimIndent()

        val shifted = SubtitleShift.shiftSrtVtt(srt, 1500, dot = false)
        val expected = """
            1
            00:00:21,500 --> 00:00:25,900
            Hello world!
        """.trimIndent()

        assertEquals(expected, shifted)
    }

    @Test
    fun `webvtt full timestamp with dot is shifted correctly`() {
        val vtt = """
            WEBVTT

            00:01:10.000 --> 00:01:15.500
            Caption with hours
        """.trimIndent()

        val shifted = SubtitleShift.shiftSrtVtt(vtt, 2000, dot = true)
        val expected = """
            WEBVTT

            00:01:12.000 --> 00:01:17.500
            Caption with hours
        """.trimIndent()

        assertEquals(expected, shifted)
    }

    @Test
    fun `webvtt without hours MM_SS_mmm is shifted correctly`() {
        val vtt = """
            WEBVTT

            00:20.000 --> 00:24.400
            Caption without hours
        """.trimIndent()

        val shifted = SubtitleShift.shiftSrtVtt(vtt, 1000, dot = true)
        val expected = """
            WEBVTT

            00:00:21.000 --> 00:00:25.400
            Caption without hours
        """.trimIndent()

        assertEquals(expected, shifted)
    }

    @Test
    fun `ass dialogue timestamps are shifted while text timestamps are preserved`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:20.00,0:00:22.00,Default,,0,0,0,,Meeting at 1:23:45.67 tomorrow!
        """.trimIndent()

        val shifted = SubtitleShift.shiftAss(ass, 1000)
        val expected = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:21.00,0:00:23.00,Default,,0,0,0,,Meeting at 1:23:45.67 tomorrow!
        """.trimIndent()

        assertEquals(expected, shifted)
    }
}
