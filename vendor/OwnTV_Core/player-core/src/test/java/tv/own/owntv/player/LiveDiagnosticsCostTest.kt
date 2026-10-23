package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class LiveDiagnosticsCostTest {
    @Test fun disabledDetailSkipsExpensiveMessageButEssentialEventsStayImmediate() {
        val previous = LiveDiagnosticsLog.enabled
        try {
            LiveDiagnosticsLog.enabled = false
            LiveDiagnosticsLog.detail { error("diagnostic-only reads must not execute") }
            LiveDiagnosticsLog.event("phase3 essential source retirement")
            assertTrue(LiveDiagnosticsLog.snapshot().contains("phase3 essential source retirement"))
        } finally { LiveDiagnosticsLog.enabled = previous }
    }
}
