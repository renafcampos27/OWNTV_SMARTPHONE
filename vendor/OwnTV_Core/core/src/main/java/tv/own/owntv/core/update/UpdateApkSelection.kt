package tv.own.owntv.core.update

/** Current release flavours: a combined ARM package and a separate x86_64 package. */
internal fun selectUpdateApk(assets: List<Pair<String, String>>, supportedAbis: List<String>): String? {
    val wantX86 = supportedAbis.firstOrNull() == "x86_64"
    if (!wantX86 && supportedAbis.none { it == "arm64-v8a" || it == "armeabi-v7a" }) return null
    return assets.firstOrNull { (name, url) ->
        name.endsWith(".apk", ignoreCase = true) && url.isNotBlank() &&
            name.contains("x86_64", ignoreCase = true) == wantX86
    }?.second
}
