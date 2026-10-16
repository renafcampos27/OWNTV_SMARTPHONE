package tv.own.owntv.core.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/** Serializes writes for the same guide source; unrelated sources remain independent. */
internal class EpgStoreLocks {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun <T> withStores(storeIds: Collection<Long>, block: suspend () -> T): T {
        val acquired = mutableListOf<Mutex>()
        try {
            for (id in storeIds.distinct().sorted()) {
                val lock = locks.computeIfAbsent(id) { Mutex() }
                lock.lock()
                acquired += lock
            }
            return block()
        } finally {
            acquired.asReversed().forEach { it.unlock() }
        }
    }
}
