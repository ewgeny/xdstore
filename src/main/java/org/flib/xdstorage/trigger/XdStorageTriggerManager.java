package org.flib.xdstorage.trigger;

import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageTriggerManager {

    private Map<Class<?>, List<IXdStorageTrigger<?>>> triggers;

    public XdStorageTriggerManager() {
        triggers = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public <T> void registerTrigger(final IXdStorageTrigger<T> trigger) {
        List<IXdStorageTrigger<?>> list = triggers.get(trigger.getClazz());
        if (list == null) {
            triggers.put(trigger.getClazz(), new ArrayList<>());
            list = triggers.get(trigger.getClazz());
        }

        synchronized (list) {
            list.add(trigger);
        }
    }

    public void performTriggers(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) {
        changes.stream().forEach(change -> {
            performTriggers(transaction, change.type, change.oldObject, change.newObject);
        });
    }

    private <T> void performTriggers(final XdStorageTransaction transaction, final XdStorageObjectOperationType type, final T oldObject, final T newObject) {
        if (type == XdStorageObjectOperationType.Insert) {
            performInsertTriggers(transaction, newObject);
        } else if (type == XdStorageObjectOperationType.Update) {
            performUpdateTriggers(transaction, oldObject, newObject);
        } else if (type == XdStorageObjectOperationType.Delete) {
            performDeleteTriggers(transaction, oldObject);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void performInsertTriggers(final XdStorageTransaction transaction, final T newObject) {
        final List<IXdStorageTrigger<?>> list = triggers.get(XdStorageObjectUtils.getEntityClass(newObject.getClass()));

        if (list != null) {
            synchronized (list) {
                for (final IXdStorageTrigger<?> trigger : list) {
                    if (trigger.getType() == XdStorageObjectOperationType.Insert) {
                        ((IXdStorageTrigger<T>) trigger)
                                .perform(null, XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject), transaction);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void performUpdateTriggers(final XdStorageTransaction transaction, final T oldObject, final T newObject) {
        final List<IXdStorageTrigger<?>> list = triggers.get(XdStorageObjectUtils.getEntityClass(oldObject.getClass()));

        if (list != null) {
            synchronized (list) {
                for (final IXdStorageTrigger<?> trigger : list) {
                    if (trigger.getType() == XdStorageObjectOperationType.Update) {
                        ((IXdStorageTrigger<T>) trigger)
                                .perform(XdStorageObjectUtils.getWrappedObjectOrSameObject(oldObject), XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject), transaction);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void performDeleteTriggers(final XdStorageTransaction transaction, final T oldObject) {
        final List<IXdStorageTrigger<?>> list = triggers.get(XdStorageObjectUtils.getEntityClass(oldObject.getClass()));

        if (list != null) {
            synchronized (list) {
                for (final IXdStorageTrigger<?> trigger : list) {
                    if (trigger.getType() == XdStorageObjectOperationType.Delete) {
                        ((IXdStorageTrigger<T>) trigger).perform(XdStorageObjectUtils.getWrappedObjectOrSameObject(oldObject), null, transaction);
                    }
                }
            }
        }
    }
}
