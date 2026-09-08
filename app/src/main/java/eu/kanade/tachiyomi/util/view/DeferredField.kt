package eu.kanade.tachiyomi.util.view

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Field that can be initialized later. Users can suspend while waiting for the field to initialize.
 *
 * @author nulldev
 */
class DeferredField<T> {

    @Volatile
    var content: T? = null

    @Volatile
    var initialized = false
        private set

    private val mutex = Mutex(true)

    /**
     * Initialize the field. Should only be called once (e.g. when a background search completes).
     * Calling this more than once is a no-op after the first call.
     */
    fun initialize(content: T) {
        if (initialized) return  // Guard: already initialized, nothing to do

        // Fast-path new listeners
        this.content = content
        initialized = true

        // Notify current listeners. The mutex starts locked (Mutex(true)),
        // so this unlock() will always succeed on first call.
        mutex.unlock()
    }

    /**
     * Update the field after it has already been initialized (e.g. manual search override).
     * Safe to call multiple times — uses tryLock() correctly and only unlocks if we won the lock.
     */
    fun set(content: T) {
        val locked = mutex.tryLock()  // Returns false if already unlocked (already initialized)
        this.content = content
        initialized = true
        // Only unlock if we successfully locked — avoids IllegalStateException
        // when set() is called after initialize() has already unlocked the mutex,
        // or when two coroutines race and one of them loses tryLock().
        if (locked) mutex.unlock()
    }

    /**
     * Will only suspend if !initialized.
     */
    suspend fun get(): T? {
        // Check if field is initialized and return immediately if it is
        if (initialized) return content

        // Wait for field to initialize
        mutex.withLock {}

        // Field is initialized, return value
        return content
    }
}
