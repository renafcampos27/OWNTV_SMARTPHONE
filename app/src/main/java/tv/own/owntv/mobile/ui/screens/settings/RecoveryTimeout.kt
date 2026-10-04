package tv.own.owntv.mobile.ui.screens.settings

internal object RecoveryTimeout {
    const val DEFAULT_SECONDS = 3
    val seconds = 1..60
    fun normalize(value: Int?): Int = (value ?: DEFAULT_SECONDS).coerceIn(seconds)
    fun milliseconds(value: Int): Long = normalize(value) * 1_000L
}
