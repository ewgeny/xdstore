package org.flib.xdstorage.transaction;

import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;

import java.util.ArrayList;
import java.util.Collection;

public class XdStorageTransactionResourceChanges {

    private final IXdStorageResourceObject resource;

    private final Collection<XdStorageObjectChange> changes;

    public XdStorageTransactionResourceChanges(final IXdStorageResourceObject resource) {
        this.resource = resource;
        this.changes = new ArrayList<>();
    }

    public IXdStorageResourceObject getResource() {
        return resource;
    }

    public void addChangeObject(final XdStorageObjectOperationType type, final Object id, final Object oldObject, final Object newObject) {
        changes.add(new XdStorageObjectChange(type, id, oldObject, newObject));
    }

    public void performTriggers(final XdStorageTransaction transaction, final XdStorageTriggerManager triggerManager) {
        triggerManager.performTriggers(transaction, changes);
    }

    public Collection<XdStorageObjectChange> getChangesObjects() {
        return changes;
    }
}
