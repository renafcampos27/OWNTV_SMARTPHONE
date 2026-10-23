package tv.own.owntv.player

import java.util.concurrent.atomic.AtomicReference

/** Latest requested view wins. Only native surface operations share this lock, never the command queue.
 * Destruction synchronously retires even an old installed view while a replacement is still queued. */
internal class NativeSurfaceBinding<S : Any, E : Any> {
    class Request<S, E>(val surface: S, val engine: E)
    private val requested = AtomicReference<Request<S, E>?>(null)
    private val nativeLock = Any()
    private var installed: Request<S, E>? = null

    fun request(surface: S, engine: E): Request<S, E> =
        Request(surface, engine).also { requested.set(it) }

    fun attach(request: Request<S, E>, valid: () -> Boolean, bind: (E, S) -> Unit, unbind: (E) -> Unit): Boolean =
        synchronized(nativeLock) {
            if (requested.get() !== request || !valid()) return false
            if (installed?.surface === request.surface && installed?.engine === request.engine) return true
            installed?.let { unbind(it.engine) }
            installed = null
            // Track before JNI: if bind throws after acquiring a native reference, destruction still retires it.
            installed = request
            bind(request.engine, request.surface)
            true
        }

    fun detach(surface: S? = null, unbind: (E) -> Unit) {
        requested.updateAndGet { if (surface == null || it?.surface === surface) null else it }
        synchronized(nativeLock) {
            val old = installed ?: return
            if (surface != null && old.surface !== surface) return
            unbind(old.engine)
            installed = null
        }
    }

    /** Destruction of the engine itself releases its native reference; queued attaches must be invalidated. */
    fun forget(engine: E) {
        requested.updateAndGet { if (it?.engine === engine) null else it }
        synchronized(nativeLock) { if (installed?.engine === engine) installed = null }
    }
}
