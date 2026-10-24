package io.github.xdstorage.btree;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;

/**
 * Entry sink of the ordered leaf-chain walks ({@code XdStorageBTree.read} and
 * {@code XdStorageBTree.readRange}): {@code look} is invoked once per entry of every
 * leaf along the chain, in ascending key order, while the walk machinery holds per-node
 * read locks. A viewer that has collected everything it needs may raise the
 * {@code XdStorageBTree.walkTerminated()} signal to stop the walk early (LIMIT pushdown);
 * the signal is treated as normal completion and never reaches the walk's caller.
 *
 * @param <TKey>    the entry key type (must satisfy the Comparable contract)
 * @param <TObject> the record type stored under the keys
 */
public interface IXdStorageBTreeViewer<TKey extends Comparable, TObject> {

    /**
     * Receives one entry of the walked leaf chain.
     *
     * @param key   the entry's key, in the tree's ascending key order
     * @param value the record stored under the key
     * @throws XdStorageException to fail the walk loudly; propagates to the walk's caller
     * @throws XdStorageConnectionException when the walk's lazy node loads hit a storage failure
     */
    void look(TKey key, TObject value) throws XdStorageException, XdStorageConnectionException;

}
