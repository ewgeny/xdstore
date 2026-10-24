package io.github.xdstorage;

/** A per-object callback for streaming scans: {@code IXdFileStorage.watch(cl, watcher)}
 * feeds every object visible in the transaction's snapshot to {@link #watch(Object)}
 * without materializing the whole result collection. Throwing from the callback fails
 * the scan.
 *
 * @param <T> the entity class the watcher observes */
public interface IXdStorageWatcher<T> {

    /**
     * Called once per object of the watched class.
     *
     * @param object the snapshot-visible object (do not mutate its stored state; use the
     *               storage API for changes)
     */
    void watch(T object);
}
