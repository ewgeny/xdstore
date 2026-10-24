package io.github.xdstorage.trigger;

import io.github.xdstorage.resource.XdStorageObjectOperationType;
import io.github.xdstorage.transaction.IXdStorageTransaction;

/** A persistence hook fired by the engine INSIDE the changing transaction when objects
 * of the trigger's class are inserted, updated or deleted. Register with
 * {@code IXdStorage.registerTrigger(...)}. A trigger must work only with the objects
 * handed to it; touching the storage requires opening and finishing its own nested
 * transaction.
 *
 * @param <T> the entity class the trigger observes */
public interface IXdStorageTrigger<T> {

    /**
     * The operation this trigger fires on.
     *
     * @return the operation type (insert/update/delete)
     */
    XdStorageObjectOperationType getType();

    /**
     * The entity class this trigger observes.
     *
     * @return the observed class
     */
    Class<T> getClazz();

    /**
     * If this method works with storage, then this one must start and finish
     * transaction.
     *
     * @param oldObject   the object's state before the change ({@code null} on insert)
     * @param newObject   the object's state after the change ({@code null} on delete)
     * @param transaction the transaction inside which the change is being made
     */
    void perform(T oldObject, T newObject, IXdStorageTransaction transaction);

}
