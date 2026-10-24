package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;

/** The id-generator SPI: assigns primary keys for entities whose
 * {@code @XdStorageObjectFieldProperties} selects {@code CUSTOM_GENERATOR} (or the
 * engine's own built-ins). Implementations need a no-arg constructor or one taking
 * {@code XdStorageServicesLocator} (reflective instantiation).
 *
 * <p>Called inside the save call, within the caller's open transaction. */
public interface IXdStorageIdGenerator {

    /**
     * Generates the next id for the class.
     *
     * @param cl          the entity class
     * @param storage     the storage asking for the id
     * @param transaction the caller's open transaction (usable for the generator's own
     *                    bookkeeping — open a NESTED transaction for storage writes)
     * @return the generated id (type must match the entity's id field)
     * @throws XdStorageException when the id cannot be generated
     */
    Object generate(Class<?> cl, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

}
