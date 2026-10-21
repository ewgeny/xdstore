package io.github.xdstorage.transaction;

import io.github.xdstorage.resource.IXdStorageResourceObject;
import io.github.xdstorage.resource.XdStorageObjectChange;
import io.github.xdstorage.resource.XdStorageObjectOperationType;
import io.github.xdstorage.trigger.XdStorageTriggerManager;

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
