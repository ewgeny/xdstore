package io.github.xdstorage.btree;

import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;

/**
 * Locking facet of the B+tree structure: the per-node read/write lock protocol implemented
 * by both {@code XdStorageBTree} (the tree-level lock) and {@code XdStorageBTreeNode}
 * (one lock per node file). Mutators take write locks along the ancestor path they may
 * rebalance; readers couple read locks hand-over-hand during descents, releasing the
 * previous node's lock once the next one is captured.
 *
 * <p>All locks are reentrant and thread-scoped: the state queries ({@code isWriteLocked},
 * {@code isReadLocked}) report the <em>calling thread's</em> hold. Blocking acquisitions
 * poll until success or the owning transaction's lock timeout expires; a timeout fails
 * loudly (unchecked exception) rather than hanging. A thread holding the write lock may
 * take read locks (downgrade); a sole reader may take the write lock (upgrade).</p>
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageBTreeNode {

    /**
     * Reports whether the calling thread currently holds this element's write lock.
     *
     * @return {@code true} when the calling thread holds the write lock (reentrancy counted)
     */
    boolean isWriteLocked();

    /**
     * Reports whether the calling thread currently holds at least one read lock
     * on this element.
     *
     * @return {@code true} when the calling thread holds the read lock (reentrancy counted)
     */
    boolean isReadLocked();

    /**
     * Acquires the write lock, blocking (with polling) until success or the owning
     * transaction's lock timeout expires. Reentrant for the owning thread.
     *
     * @param transaction the owning transaction providing the lock timeout ({@code null} — engine default)
     * @throws XdStorageException when the acquiring thread is interrupted while waiting
     */
    void lockWrite(IXdStorageTransaction transaction) throws XdStorageException;

    /**
     * Acquires the read lock, blocking (with polling) until success or the owning
     * transaction's lock timeout expires. Reentrant; granted immediately to the thread
     * holding the write lock.
     *
     * @param transaction the owning transaction providing the lock timeout ({@code null} — engine default)
     * @throws XdStorageException when the acquiring thread is interrupted while waiting
     */
    void lockRead(IXdStorageTransaction transaction) throws XdStorageException;

    /**
     * Attempts to acquire the write lock without waiting for other holders.
     *
     * @param transaction the owning transaction (part of the interface contract; not used by the try-variant)
     * @return {@code true} when the lock was captured, {@code false} when another thread
     *         holds the write lock or other readers hold read locks
     */
    boolean tryLockWrite(IXdStorageTransaction transaction);

    /**
     * Attempts to acquire the read lock without waiting for other holders.
     *
     * @param transaction the owning transaction (part of the interface contract; not used by the try-variant)
     * @return {@code true} when the lock was captured, {@code false} when another thread holds the write lock
     */
    boolean tryLockRead(IXdStorageTransaction transaction);

    /**
     * Releases one write-lock hold of the calling thread. Unbalanced release
     * (nothing held) fails loudly.
     */
    void unlockWrite();

    /**
     * Releases one read-lock hold of the calling thread. Unbalanced release
     * (nothing held) fails loudly.
     */
    void unlockRead();

}
