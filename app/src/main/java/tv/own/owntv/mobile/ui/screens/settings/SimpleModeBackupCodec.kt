package tv.own.owntv.mobile.ui.screens.settings

import org.json.JSONObject

/** Explicit schema: every portable SimpleModeOptions field, including false/default values. */
object SimpleModeBackupCodec {
    const val KEY = "ownTvSimpleMode"

    fun encode(options: SimpleModeOptions): JSONObject = JSONObject()
        .put("version", 1)
        .put("hideCategories", options.hideCategories)
        .put("hideSidebar", options.hideSidebar)
        .put("channelRecovery", options.channelRecovery)
        .put("recoveryTimeoutSeconds", RecoveryTimeout.normalize(options.recoveryTimeoutSeconds))
        .put("startOnBoot", options.startOnBoot)
        .put("startOnWake", options.startOnWake)

    // Retired experimental fields in older backups are intentionally ignored.
    fun decode(data: JSONObject, current: SimpleModeOptions): SimpleModeOptions {
        val version = data.get("version")
        require(version is Number && version.toDouble() == 1.0) { "Unsupported simple-mode backup version" }
        fun flag(key: String, fallback: Boolean): Boolean {
            if (!data.has(key)) return fallback
            val value = data.get(key)
            require(value is Boolean) { "Invalid simple-mode boolean" }
            return value
        }
        val timeout = if (data.has("recoveryTimeoutSeconds")) {
            val value = data.get("recoveryTimeoutSeconds")
            require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 &&
                value.toDouble() in 1.0..60.0) { "Invalid channel recovery timeout" }
            value.toInt()
        } else current.recoveryTimeoutSeconds
        return current.copy(
            hideCategories = flag("hideCategories", current.hideCategories),
            hideSidebar = flag("hideSidebar", current.hideSidebar),
            channelRecovery = flag("channelRecovery", current.channelRecovery),
            recoveryTimeoutSeconds = timeout,
            startOnBoot = false,
            startOnWake = false,
        )
    }
}
