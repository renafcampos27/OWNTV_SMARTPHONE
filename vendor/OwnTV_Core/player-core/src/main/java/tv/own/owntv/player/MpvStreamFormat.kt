package tv.own.owntv.player

import tv.own.owntv.core.player.ManifestType

/** Declared HLS and HLS URLs are not continuous TS, even when their path ends in .ts. */
internal object MpvStreamFormat {
    fun isHls(url: String, live: Boolean, manifest: ManifestType?, strict: Boolean = false, learned: Boolean = false): Boolean =
        manifest == ManifestType.HLS || LiveStreamQuirks.isExplicitHlsUrl(url) || live && (strict || learned)
}
