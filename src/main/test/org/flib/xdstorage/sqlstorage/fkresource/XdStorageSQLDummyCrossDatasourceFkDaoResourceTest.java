package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Полностью обратно-совместимый JUnit 5 тест для класса XdStorageSQLDummyCrossDatasourceFkDaoResource.
 * Успешно компилируется на старых версиях Java без поддержки ключевого слова var.
 */
public class XdStorageSQLDummyCrossDatasourceFkDaoResourceTest {

    @Test
    public void testSingletonInstance_ShouldReturnSameObject() {
        // ИСПРАВЛЕНИЕ: Явное указание типов вместо ключевого слова var
        org.flib.xdstorage.resource.IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> instance1 =
                XdStorageSQLDummyCrossDatasourceFkDaoResource.getInstance();
        org.flib.xdstorage.resource.IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> instance2 =
                XdStorageSQLDummyCrossDatasourceFkDaoResource.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    public void testResourceMetadata_ShouldReturnStaticDefaults() throws XdStorageException {
        // ИСПРАВЛЕНИЕ: Явное указание типов
        org.flib.xdstorage.resource.IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> dummy =
                XdStorageSQLDummyCrossDatasourceFkDaoResource.getInstance();

        assertEquals(XdStorageSQLCrossDatasourceFk.class, dummy.getObjectsClass());
        assertEquals(0, dummy.getObjectsCount());
        assertFalse(dummy.hasChanges(mock(XdStorageTransaction.class)));
        assertSame(dummy, dummy.getDao());
    }

    @Test
    public void testOperations_ShouldExecuteWithoutExceptionsOrSideEffects() {
        // ИСПРАВЛЕНИЕ: Явное указание типов
        org.flib.xdstorage.resource.IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> dummy =
                XdStorageSQLDummyCrossDatasourceFkDaoResource.getInstance();
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        // Все транзакционные методы Null-Object обязаны безопасно завершаться, ничего не делая
        assertDoesNotThrow(() -> dummy.prepare(mockTx));
        assertDoesNotThrow(() -> dummy.performFirstPhaseCommit(mockTx, mock(XdStorageTransactionResourceChanges.class)));
        assertDoesNotThrow(() -> dummy.performSecondPhaseCommit(mockTx, mock(XdStorageTransactionResourceChanges.class)));
        assertDoesNotThrow(() -> dummy.rollbackPerformingFirstPhaseCommit(mockTx, new ArrayList<>()));
        assertDoesNotThrow(() -> dummy.rollback(mockTx));
        assertDoesNotThrow(() -> dummy.lockForCommit(mockTx));
        assertDoesNotThrow(() -> dummy.unlockAfterCommit(mockTx));
        assertDoesNotThrow(() -> dummy.release(mockTx));
    }
}
