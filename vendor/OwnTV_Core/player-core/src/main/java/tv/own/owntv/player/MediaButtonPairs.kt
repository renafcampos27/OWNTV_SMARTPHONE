package tv.own.owntv.player

/** Some remotes send only UP. Preserve platform handling of complete DOWN/UP pairs. */
internal class MediaButtonPairs {
    private val pressed = mutableMapOf<Int, Long>()

    @Synchronized fun orphanUp(key: Int, down: Boolean, downTime: Long): Boolean {
        if (down) {
            pressed[key] = downTime
            return false
        }
        return pressed.remove(key) != downTime
    }

    @Synchronized fun reset() = pressed.clear()
}
