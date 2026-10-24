package io.github.xdstorage.index;

/**
 * One compensating change captured while the id-index mutates its in-memory id-to-fragment
 * map inside a transaction: {@link #rollback()} undoes exactly that one mutation when the
 * transaction rolls back. Implementations are pushed onto the per-transaction rollback stack
 * kept by the hash id-index resource ({@code XdStorageAbstractHashIndexResource}) and are
 * never invoked outside a rollback.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageChangeIndexRollback {

    /**
     * Undoes the single index mutation this compensation was registered for. Called by
     * the owning resource while a transaction rolls back; must not throw.
     */
    void rollback();

}
