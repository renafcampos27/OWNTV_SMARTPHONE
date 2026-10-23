package tv.own.owntv.player

/** Per-load native options; an archive's keyframe wait must never carry into the following live tune. */
internal data class MpvProbeProfile(
    val trimmedRawTs: Boolean,
    val lavcOptions: String,
    val probeSize: String,
    val analyzeDuration: String,
) {
    companion object {
        fun forLoad(live: Boolean, archive: Boolean, rawTs: Boolean, forceFullProbe: Boolean, hardwareDecoding: Boolean = true): MpvProbeProfile {
            val trimmed = live && rawTs && !archive && !forceFullProbe
            return MpvProbeProfile(
                trimmedRawTs = trimmed,
                // OwnTV libmpv 2026.10.4: skip missing-reference slices, at most 300 packets.
                // The setting is written on every load, including an empty reset for non-archives.
                lavcOptions = if (archive && !live && hardwareDecoding) "wait_keyframe=1" else "",
                probeSize = if (trimmed) "1000000" else "5000000",
                analyzeDuration = if (trimmed) "1.0" else "0",
            )
        }
    }
}
