package tv.own.owntv.mobile.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the OwnTV Mobile baseline profile.
 *
 * Everything this journey touches is AOT-compiled into the shipped APK, so the Compose runtime,
 * Room's query machinery, Koin's graph resolution and the shell composition no longer run
 * interpreted while the user waits for the first screen.
 *
 * ```powershell
 * ./gradlew :app:generateBaselineProfile
 * ```
 *
 * `mergeIntoMain = true` in `app/build.gradle.kts` collapses the per-variant tasks into that one and
 * writes the result to `app/src/main/generated/baselineProfiles/`. **Regenerate it whenever the
 * startup path changes materially** — a stale profile quietly stops helping, it does not fail the
 * build.
 *
 * ### Two things this journey depends on, both learned the hard way on the television
 *
 * **1. The app must already be set up, with a catalog.** A fresh install opens the first-run wizard,
 * and a blind journey never escapes it — the profile then records the wizard instead of the app.
 * That is exactly what happened to the television's first recording: 555 entries for settings, 79
 * for setup, *one* each for home, movies, series, live and search. The Gradle task reinstalls the
 * same APK with `install -r`, which keeps existing data, so record on a device where the app is
 * already configured.
 *
 * **2. It is driven by coordinates, not by text or resource ids.** This app has no test tags, and
 * matching on a label would tie the recording to one language and one catalog. Taps are placed by
 * fraction of the screen, so the same journey runs on any phone, in any language, with any playlist
 * — and cannot fail on a device whose catalog is empty. The cost is that a tap can land on a
 * neighbouring tab; that is acceptable, because every bottom-bar destination is visited anyway and
 * the profile is a union of what was reached, not a script that must match.
 *
 * The number of bottom-bar destinations is **not fixed** — `MainSection.dynamicVisible()` hides Live
 * and Guide when a playlist has no channels, and Movies/Series when their tables are empty — which
 * is the other reason nothing here counts tabs or asserts on what it found.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Also emit the startup profile — the subset ART compiles before the first frame.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        settle()

        // Home first, scrolled for real: the hero card, the row headers and the poster items are the
        // first composition the user ever waits for.
        scrollContent()

        // Then each bottom-bar destination in turn, scrolling inside it. Entering the content is
        // what matters — a screen whose grid never composes contributes nothing but its scaffold.
        for (slot in 0 until NAV_SLOTS) {
            tapNavSlot(slot)
            settle()
            scrollContent()
        }

        // Open something and come back: the detail screen, its images and the back path all get
        // compiled. A tap in the middle of a browse screen lands on a row or a poster whatever the
        // screen turned out to be.
        device.click(device.displayWidth / 2, (device.displayHeight * CONTENT_TAP_Y).toInt())
        Thread.sleep(OPEN_SETTLE_MS)
        device.pressBack()
        settle()

        // Back to the first tab, so the recording ends on the screen the app opens on.
        tapNavSlot(0)
        settle()
    }

    /**
     * Taps the middle of bottom-bar slot [slot] of [NAV_SLOTS], across the width of the nav island.
     *
     * The y position is a fraction rather than a dp offset because the island's distance from the
     * bottom is its own inset plus the device's gesture inset, and that second part varies by phone.
     * [NAV_Y] sits inside the island on a three-button and a gesture-navigation device alike.
     */
    private fun MacrobenchmarkScope.tapNavSlot(slot: Int) {
        val x = device.displayWidth * (slot * 2 + 1) / (NAV_SLOTS * 2)
        device.click(x, (device.displayHeight * NAV_Y).toInt())
    }

    /** Scroll the content area down and back up, so lazy lists actually compose their items. */
    private fun MacrobenchmarkScope.scrollContent() {
        val x = device.displayWidth / 2
        val top = (device.displayHeight * SCROLL_TOP).toInt()
        val bottom = (device.displayHeight * SCROLL_BOTTOM).toInt()
        repeat(SCROLL_STEPS) {
            device.swipe(x, bottom, x, top, SWIPE_STEPS)
            device.waitForIdle()
        }
        settle()
        repeat(SCROLL_STEPS) {
            device.swipe(x, top, x, bottom, SWIPE_STEPS)
            device.waitForIdle()
        }
        settle()
    }

    /** Give Compose a beat to actually run the frames being recorded. */
    private fun MacrobenchmarkScope.settle() {
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    private companion object {
        const val PACKAGE = "tv.own.owntv.mobile"

        /** The bottom bar's full complement. Fewer are drawn when a playlist has less in it. */
        const val NAV_SLOTS = 5

        /** Inside the nav island, clear of the gesture area below it. */
        const val NAV_Y = 0.94f

        /** The vertical span swiped, kept clear of the top bar and the nav island. */
        const val SCROLL_TOP = 0.30f
        const val SCROLL_BOTTOM = 0.80f

        /** Where a tap lands to open a row or a poster: above the fold, below the top bar. */
        const val CONTENT_TAP_Y = 0.45f

        const val SCROLL_STEPS = 4
        const val SWIPE_STEPS = 12
        const val SETTLE_MS = 400L
        const val OPEN_SETTLE_MS = 2_500L
    }
}
