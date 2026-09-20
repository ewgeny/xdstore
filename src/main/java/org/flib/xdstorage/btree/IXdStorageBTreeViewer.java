package org.flib.xdstorage.btree;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageBTreeViewer<TKey extends Comparable, TObject> {

    void look(TKey key, TObject value) throws XdStorageException, XdStorageConnectionException;

}
