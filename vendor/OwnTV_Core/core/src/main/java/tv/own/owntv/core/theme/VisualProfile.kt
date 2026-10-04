package tv.own.owntv.core.theme

/** UI-only budget. Never changes decoding, network, stream quality or player buffers. */
enum class VisualProfile { AUTO, LIGHT, FULL }

object VisualProfilePolicy {
    fun isLight(profile: VisualProfile, lowRam: Boolean, totalMemoryBytes: Long): Boolean =
        profile == VisualProfile.LIGHT || (profile == VisualProfile.AUTO &&
            (lowRam || totalMemoryBytes in 1..(3L * 1024 * 1024 * 1024)))

    fun animation(requested: AnimationLevel, light: Boolean): AnimationLevel =
        if (light) AnimationLevel.OFF else requested

    fun glass(requested: GlassConfig, light: Boolean): GlassConfig =
        if (light) requested.copy(blurStrength = 0f, depthEffects = false, glint = false) else requested
}
