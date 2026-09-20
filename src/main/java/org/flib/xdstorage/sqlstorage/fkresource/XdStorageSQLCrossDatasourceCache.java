package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageSQLCrossDatasourceCache {

    /**
     * map [transaction, list [foreign_key] ]
     */
    private final Map<String, List<XdStorageSQLCrossDatasourceFk>> toInsert = new ConcurrentHashMap<>();

    /**
     * map [transaction, map [child_object_id, foreign_key] ]
     */
    private final Map<String, Map<Object, XdStorageSQLCrossDatasourceFk>> toDelete = new ConcurrentHashMap<>();

    public void insert(final XdStorageSQLCrossDatasourceFk foreignKey, final XdStorageTransaction transaction) {
        final List<XdStorageSQLCrossDatasourceFk> list = toInsert.computeIfAbsent(transaction.getTransactionId(), tid -> Collections.synchronizedList(new LinkedList<>()));
        list.add(foreignKey);
    }

    public void delete(final XdStorageSQLCrossDatasourceFk foreignKey, final XdStorageTransaction transaction) throws XdStorageException {
        final Map<Object, XdStorageSQLCrossDatasourceFk> map = toDelete.computeIfAbsent(transaction.getTransactionId(), tid -> new ConcurrentHashMap<>());

        final Object childObject = foreignKey.getChildObject();
        final XdStorageClassInfo info = XdStorageObjectUtils.getClassInfo(childObject.getClass());
        final XdStorageObjectIdField idField = info.getIdField();

        final Object childId = idField.get(childObject);
        if (childId == null)
        {
            throw new XdStorageException("cannot register foreign key for deletion: identifier of child object is null");
        }

        map.put(childId, foreignKey);
    }

    public boolean hasChanges(final XdStorageTransaction transaction)
    {
        final List<XdStorageSQLCrossDatasourceFk> toInsertList = toInsert.get(transaction.getTransactionId());
        final Map<Object, XdStorageSQLCrossDatasourceFk> toDeleteMap = toDelete.get(transaction.getTransactionId());

        return (toInsertList != null && !toInsertList.isEmpty()) || (toDeleteMap != null && !toDeleteMap.isEmpty());
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges changes) {
        final List<XdStorageSQLCrossDatasourceFk> list = toInsert.get(transaction.getTransactionId());
        if (list != null && !list.isEmpty()) {
            list.forEach(fk -> changes.addChangeObject(XdStorageObjectOperationType.Insert, null, null, fk));
        }

        final Map<Object, XdStorageSQLCrossDatasourceFk> map = toDelete.get(transaction.getTransactionId());
        if (map != null && !map.isEmpty()) {
            map.forEach( (childObjectId, fk) -> changes.addChangeObject(XdStorageObjectOperationType.Delete, null, fk, null));
        }
    }

    public void rollback(final XdStorageTransaction transaction) {
        toInsert.remove(transaction.getTransactionId());
        toDelete.remove(transaction.getTransactionId());
    }

    public void rollbackFailedCommit(final XdStorageTransaction transaction) {
        rollback(transaction);
    }

    public void performCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) {
        // do nothing
    }


    public void commit(final XdStorageTransaction transaction) {
        toInsert.remove(transaction.getTransactionId());
        toDelete.remove(transaction.getTransactionId());
    }

    public void clear(final XdStorageTransaction transaction) {
        toInsert.remove(transaction.getTransactionId());
        toDelete.remove(transaction.getTransactionId());
    }
}
