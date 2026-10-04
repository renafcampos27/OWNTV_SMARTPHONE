package tv.own.owntv.core.timeshift

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import tv.own.owntv.core.network.cancelBlockingCallWithCoroutine
import tv.own.owntv.core.network.HttpRetryAfter
import tv.own.owntv.core.recording.HlsMediaPlaylist
import tv.own.owntv.core.recording.HlsRecordingPlan
import tv.own.owntv.core.recording.HlsSegmentTransfer
import tv.own.owntv.core.recording.HlsRangeValidation
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.TimeUnit

/** One upstream HLS producer, any number of independent local readers, bounded to one session. */
class LocalTimeshiftSession(
    directory: File,
    client: OkHttpClient,
    private val upstreamUrl: String,
    private val headers: Map<String, String>,
    reserveBytes: Long = tv.own.owntv.core.storage.StorageQuotaPolicy.reserve(
        generateSequence(directory) { it.parentFile }.firstOrNull { it.exists() }?.totalSpace ?: 0, removable = false),
    private val variantSupported: (HlsMediaPlaylist.Variant) -> Boolean = HlsVariantSupport::deviceSupports,
) {
    enum class Status { STARTING, READY, ENDED, FAILED, CLOSED }
    private val store = LocalSegmentStore(directory, reserveBytes = reserveBytes)
    private val server = LocalTimeshiftServer(store)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = client.newBuilder().connectionPool(ConnectionPool(2, 5, TimeUnit.SECONDS))
        // Own transport retries and surface status failures before OkHttp's automatic 503 follow-up.
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code >= 400) {
                val failure = HttpFailure(response.code, HttpRetryAfter.delayMs(response.header("Retry-After")))
                response.close()
                throw failure
            }
            response
        }
        .callTimeout(20, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val _status = MutableStateFlow(Status.STARTING)
    val status = _status.asStateFlow()
    val url: String get() = server.url
    private val ready = CompletableDeferred<Unit>()
    private val variantIdentities = mutableListOf<String>()
    private val producer: Job = scope.launch {
        try {
            capture()
            store.finish()
            http.connectionPool.evictAll()
            _status.value = Status.ENDED
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            store.finish()
            http.connectionPool.evictAll()
            _status.value = Status.FAILED
            if (!ready.isCompleted) ready.completeExceptionally(error)
        }
    }
    // The producer bounds retries and provider waits; slow target durations may need >35 seconds.
    suspend fun awaitReady() = withTimeout(LocalTimeshiftRecovery.MAX_NO_PROGRESS_MS) { ready.await() }
    fun retainedDurationMs(): Long = store.snapshot().sumOf { it.durationMs }
    fun bytesOnDisk(): Long = store.bytesOnDisk()
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        // Cancellation closes the current HTTP call before storage or leases can be reused.
        ready.cancel()
        producer.cancelAndJoin()
        http.connectionPool.evictAll()
        server.closeAndAwait()
        store.close()
        scope.cancel()
        _status.value = Status.CLOSED
    }
    // A status refusal must bypass OkHttp's transport retry machinery, including 503:0 follow-ups.
    private class HttpFailure(val code: Int, val retryAfterMs: Long?) : ProtocolException("HTTP $code")
    private suspend fun <T> fetch(url: String, range: HlsMediaPlaylist.ByteRange? = null, read: (Response) -> T): T {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            range?.let { header("Range", it.header) }
        }.build()
        val call = http.newCall(request)
        return cancelBlockingCallWithCoroutine(call::cancel) {
            call.execute().use { response ->
                if (!response.isSuccessful) throw HttpFailure(response.code, HttpRetryAfter.delayMs(response.header("Retry-After")))
                if (range != null) check(HlsRangeValidation.accepts(response.code, response.header("Content-Range"), range))
                read(response)
            }
        }
    }
    private suspend fun playlist(url: String): Pair<String, HlsMediaPlaylist> = fetch(url) { response ->
        val input = response.body.byteStream()
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            check(out.size() + count <= 1024 * 1024)
            out.write(chunk, 0, count)
        }
        val bytes = out.toByteArray()
        check(bytes.size <= 1024 * 1024)
        val text = bytes.toString(Charsets.UTF_8)
        check(HlsMediaPlaylist.looksLikePlaylist(response.body.contentType()?.toString(), text))
        response.request.url.toString() to HlsMediaPlaylist.parse(text)
    }
    private fun resolve(base: String, path: String): String =
        base.toHttpUrl().resolve(path)?.toString() ?: error("Invalid HLS reference")
    private data class Snapshot(val base: String, val media: HlsMediaPlaylist)
    /** Refresh the stable entry point so redirect targets and signed variant queries can rotate. */
    private suspend fun loadSnapshot(): Snapshot {
        var (base, media) = playlist(upstreamUrl)
        var depth = 0
        while (media.isMaster) {
            check(!media.invalid && depth < 3)
            val variant = HlsRecordingPlan.selectVariant(media, variantIdentities.getOrNull(depth), variantSupported)
                ?: error("Separate HLS audio or a changed variant is unsupported")
            if (depth == variantIdentities.size) variantIdentities.add(HlsRecordingPlan.identity(variant))
            val selected = playlist(resolve(base, variant.uri))
            base = selected.first; media = selected.second; depth++
        }
        check(!media.invalid && !media.isEncrypted && media.segments.none { it.init != null })
        return Snapshot(base, media)
    }
    private suspend fun retryDelay(error: IOException, attempt: Int, segment: Boolean) {
        currentCoroutineContext().ensureActive()
        if (error is HttpFailure) {
            val retryable = (error.code in 500..599 && error.code != 509) || error.code == 408 || error.code == 429 ||
                (segment && error.code in listOf(403, 404, 410))
            if (!retryable) throw error
        }
        val providerDelay = (error as? HttpFailure)?.retryAfterMs
        // Stop rather than reconnect earlier than a provider's long cooldown.
        if (providerDelay != null && providerDelay > LocalTimeshiftRecovery.MAX_PROVIDER_WAIT_MS) throw error
        delay(providerDelay ?: if (error is HttpFailure && error.code == 429) 5_000L else 500L * (attempt + 1))
    }
    private suspend fun refresh(): Snapshot {
        repeat(LocalTimeshiftRecovery.ATTEMPTS) { attempt ->
            try { return loadSnapshot() }
            catch (error: IOException) {
                if (attempt == LocalTimeshiftRecovery.ATTEMPTS - 1) throw error
                retryDelay(error, attempt, segment = false)
            }
        }
        error("HLS refresh exhausted")
    }
    private suspend fun download(snapshot: Snapshot, segment: HlsMediaPlaylist.Segment, part: File) {
        val copyContext = currentCoroutineContext()
        fetch(resolve(snapshot.base, segment.uri), segment.range) { response ->
            val body = response.body
            val length = segment.range?.length ?: body.contentLength().takeIf { it >= 0 }
            require(length == null || length in 1..HlsSegmentTransfer.MAX_SEGMENT_BYTES)
            if (length != null) store.prepareIncoming(length)
            HlsSegmentTransfer.copy(body.byteStream(), part, length,
                beforeWrite = { written, additional ->
                    store.prepareIncoming(length ?: (written + additional), written)
                }) { copyContext.ensureActive() }
        }
        copyContext.ensureActive()
        check(HlsSegmentTransfer.isTransportStream(part))
    }
    private suspend fun capture() {
        var snapshot = refresh()
        var nextSequence: Long? = null
        var lastDiscontinuity: Long? = null
        var pendingGap = false
        var lastCommitNs = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            val media = snapshot.media
            check(System.nanoTime() - lastCommitNs < LocalTimeshiftRecovery.noProgressMs(media.targetDurationSecs) * 1_000_000L)
            val previousNext = nextSequence
            // A sequence reset is a new stream, never append duplicates or invent continuity.
            if (previousNext != null && media.segments.isNotEmpty()) check(media.segments.last().sequence >= previousNext - 1)
            for (segment in media.segments) {
                currentCoroutineContext().ensureActive()
                // A wrapped next-sequence would repeatedly commit this window without polling.
                check(segment.sequence in 0 until Long.MAX_VALUE)
                if (nextSequence != null && segment.sequence < nextSequence) continue
                val gap = nextSequence != null && segment.sequence != nextSequence
                if (segment.gap) { nextSequence = segment.sequence + 1; pendingGap = true; continue }
                val part = File(store.directory, "incoming.part")
                try {
                    val latest = snapshot.media.segments.firstOrNull { it.sequence == segment.sequence }
                    if (latest == null) {
                        check(snapshot.media.segments.firstOrNull()?.sequence?.let { it > segment.sequence } == true)
                        nextSequence = segment.sequence + 1
                        pendingGap = true
                        continue
                    }
                    if (latest.gap) { nextSequence = segment.sequence + 1; pendingGap = true; continue }
                    var candidate: HlsMediaPlaylist.Segment = latest
                    var committed = false
                    for (attempt in 0 until LocalTimeshiftRecovery.ATTEMPTS) {
                        try {
                            download(snapshot, candidate, part)
                            committed = true
                            break
                        } catch (error: IOException) {
                            part.delete()
                            if (attempt == LocalTimeshiftRecovery.ATTEMPTS - 1) throw error
                            retryDelay(error, attempt, segment = true)
                            // Exactly the failed sequence is retried with fresh references, never advance on I/O failure.
                            snapshot = refresh()
                            val updated = snapshot.media.segments.firstOrNull { it.sequence == segment.sequence }
                            if (updated == null) {
                                check(snapshot.media.segments.firstOrNull()?.sequence?.let { it > segment.sequence } == true)
                                pendingGap = true
                                break
                            }
                            if (updated.gap) { pendingGap = true; break }
                            candidate = updated
                        }
                    }
                    if (!committed) { nextSequence = segment.sequence + 1; continue }
                    currentCoroutineContext().ensureActive()
                    val discontinuity = gap || pendingGap || (lastDiscontinuity != null && lastDiscontinuity != candidate.discontinuity)
                    store.commit(part, HlsRecordingPlan.durationMs(candidate), discontinuity)
                    nextSequence = segment.sequence + 1
                    lastDiscontinuity = candidate.discontinuity
                    pendingGap = false
                    lastCommitNs = System.nanoTime()
                    if (store.snapshot().size >= 3 || snapshot.media.endList) {
                        _status.value = Status.READY
                        ready.complete(Unit)
                    }
                } finally { part.delete() }
            }
            if (snapshot.media.endList && snapshot.media.segments.all { nextSequence != null && it.sequence < nextSequence }) {
                if (!ready.isCompleted && store.snapshot().isNotEmpty()) {
                    _status.value = Status.READY
                    ready.complete(Unit)
                }
                if (!ready.isCompleted) error("Empty HLS stream")
                return
            }
            // A retry refresh may already contain an unprocessed tail; consume it before polling again.
            if (snapshot.media.segments.any { nextSequence == null || it.sequence >= nextSequence }) continue
            delay(media.pollIntervalMs)
            snapshot = refresh()
        }
    }
}
