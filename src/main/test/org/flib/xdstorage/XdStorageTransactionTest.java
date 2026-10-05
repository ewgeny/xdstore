package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("1. Спецификации транзакционного контура и триггеров XdStorage")
public class XdStorageTransactionTest extends AbstractXdStorageTest {

    @BeforeEach
    public void localSetUp() throws Exception {
        // Повторно инициализируем базовый класс для изоляции стейта
        baseSetUp();
    }

    @Test
    @DisplayName("Метод beginTransaction() обязан делегировать вызовы в TransactionsManager")
    public void testTransaction_Begin_ShouldForwardToManager() {
        storage.beginTransaction();
        verify(mockTxManager, times(1)).beginTransaction(10000L);

        storage.beginTransaction(5000L);
        verify(mockTxManager, times(1)).beginTransaction(5000L);
    }

    @Test
    @DisplayName("Методы commit/rollback обязаны падать при передаче null транзакции")
    public void testTransaction_CommitRollback_WithNull_ShouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> storage.commitTransaction(null));
        assertThrows(IllegalArgumentException.class, () -> storage.rollbackTransaction(null));
    }

    @Test
    @DisplayName("Метод commitTransaction() обязан транслировать вызов фиксации сессии")
    public void testTransaction_Commit_HappyPath_ShouldInvokeManager() throws Exception {
        storage.commitTransaction(mockTx);
        verify(mockTxManager, times(1)).commitTransaction(mockTx);
    }

    @Test
    @DisplayName("Метод registerTrigger() обязан бесшовно прокидывать триггеры в менеджер")
    public void testTriggers_Registration_ShouldForwardToTriggersManager() {
        org.flib.xdstorage.trigger.IXdStorageTrigger<Object> mockTrigger = mock(org.flib.xdstorage.trigger.IXdStorageTrigger.class);
        storage.registerTrigger(mockTrigger);
        verify(mockTriggersManager, times(1)).registerTrigger(mockTrigger);
    }
}
