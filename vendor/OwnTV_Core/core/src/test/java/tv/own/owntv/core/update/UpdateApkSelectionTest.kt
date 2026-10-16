package tv.own.owntv.core.update

import org.junit.Assert.*
import org.junit.Test

class UpdateApkSelectionTest {
    private val arm = "OwnTV-Mobile-v1.0.2.apk" to "https://example.test/arm.apk"
    private val x86 = "OwnTV-Mobile-x86_64-v1.0.2.apk" to "https://example.test/x86.apk"

    @Test fun armUsesCombinedPackage() {
        assertEquals(arm.second, selectUpdateApk(listOf(x86, arm), listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(arm.second, selectUpdateApk(listOf(arm), listOf("armeabi-v7a")))
    }
    @Test fun emulatorUsesItsOwnPackage() {
        assertEquals(x86.second, selectUpdateApk(listOf(arm, x86), listOf("x86_64")))
    }
    @Test fun neverFallsBackToIncompatibleArchitecture() {
        assertNull(selectUpdateApk(listOf(arm), listOf("x86_64")))
        assertNull(selectUpdateApk(listOf(x86), listOf("arm64-v8a")))
        assertNull(selectUpdateApk(listOf(arm, x86), listOf("x86")))
    }
    @Test fun missingOrInvalidAssetsAreRejected() {
        assertNull(selectUpdateApk(emptyList(), listOf("arm64-v8a")))
        assertNull(selectUpdateApk(listOf("notes.txt" to "https://example.test/notes"), listOf("arm64-v8a")))
        assertNull(selectUpdateApk(listOf(arm.first to ""), listOf("arm64-v8a")))
    }
}
