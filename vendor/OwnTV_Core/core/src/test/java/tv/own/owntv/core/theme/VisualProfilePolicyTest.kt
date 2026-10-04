package tv.own.owntv.core.theme

import org.junit.Assert.*
import org.junit.Test

class VisualProfilePolicyTest {
    @Test fun `automatic budget follows physical memory and low ram flag`() {
        assertTrue(VisualProfilePolicy.isLight(VisualProfile.AUTO, false, 2L * 1024 * 1024 * 1024))
        assertTrue(VisualProfilePolicy.isLight(VisualProfile.AUTO, false, 3L * 1024 * 1024 * 1024))
        assertFalse(VisualProfilePolicy.isLight(VisualProfile.AUTO, false, 4L * 1024 * 1024 * 1024))
        assertTrue(VisualProfilePolicy.isLight(VisualProfile.AUTO, true, 4L * 1024 * 1024 * 1024))
        assertFalse(VisualProfilePolicy.isLight(VisualProfile.AUTO, false, 0))
    }
    @Test fun `explicit profile overrides automatic budget`() {
        assertFalse(VisualProfilePolicy.isLight(VisualProfile.FULL, true, 1))
        assertTrue(VisualProfilePolicy.isLight(VisualProfile.LIGHT, false, 8L * 1024 * 1024 * 1024))
    }
    @Test fun `effective budget preserves user choices and all unrelated properties`() {
        val glass = GlassConfig(scope = setOf(GlassSurface.CARDS), alpha = 0.7f, blurStrength = 0.8f)
        val light = VisualProfilePolicy.glass(glass, true)
        assertEquals(glass.scope, light.scope)
        assertEquals(glass.alpha, light.alpha)
        assertFalse(light.glint); assertFalse(light.depthEffects)
        assertEquals(0f, light.blurStrength)
        assertEquals(glass, VisualProfilePolicy.glass(glass, false))
        assertEquals(AnimationLevel.OFF, VisualProfilePolicy.animation(AnimationLevel.FULL, true))
        assertEquals(AnimationLevel.OFF, VisualProfilePolicy.animation(AnimationLevel.OFF, false))
    }
}
