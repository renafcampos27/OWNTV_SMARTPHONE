package tv.own.owntv.mobile.images

/** Images have an independent, bounded share of the app heap and disposable cache disk. */
internal object ImageCacheBudget {
    private const val MIB = 1024L * 1024L

    fun memoryBytes(heapBytes: Long, lowRam: Boolean): Long =
        (heapBytes.coerceAtLeast(16 * MIB) / if (lowRam) 16 else 10)
            .coerceIn(1 * MIB, (if (lowRam) 16 else 48) * MIB)

    fun diskBytes(freeBytes: Long, lowRam: Boolean): Long {
        if (freeBytes < 64 * MIB) return 0L
        return minOf(freeBytes / 50, (if (lowRam) 32 else 96) * MIB)
    }
}
