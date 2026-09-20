package org.flib.xdstorage.transaction;

import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Потокобезопасный коллектор изменений транзакционных ресурсов.
 * Защищен от Race Conditions на фазе фиксации 2PC.
 */
public class XdStorageTransactionResourceChanges {

    private final IXdStorageResourceObject resource;

    /**
     * ИСПРАВЛЕНИЕ: Перевод на ConcurrentLinkedQueue для безопасной конкурентной записи.
     */
    private final Queue<XdStorageObjectChange> changes;

    public XdStorageTransactionResourceChanges(final IXdStorageResourceObject resource) {
        this.resource = resource;
        this.changes = new ConcurrentLinkedQueue<>();
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
