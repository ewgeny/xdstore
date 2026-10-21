package io.github.xdstorage.btree;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;

public interface IXdStorageBTreeViewer<TKey extends Comparable, TObject> {

    void look(TKey key, TObject value) throws XdStorageException, XdStorageConnectionException;

}
