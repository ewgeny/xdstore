package org.flib.xdstorage.sqlstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.factories.XdStorageSmartCloner;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Исправленный комплект JUnit 5 тестов для проверки реального поведения XdStorageSQLTransactionManager.
 * Полностью учитывает логику аварийной компенсации ресурсов через процессор на этапе отката.
 */
public class XdStorageSQLTransactionManagerTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSQLProcessor mockProcessor;
    private XdStorageSQLTransactionManager sqlTxManager;
    private ExecutorService testExecutor;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockProcessor = mock(XdStorageSQLProcessor.class);
        testExecutor = Executors.newSingleThreadExecutor();
        XdStorageSmartCloner mockCloner = mock(XdStorageSmartCloner.class);
        XdStorageTriggerManager mockTriggers = mock(XdStorageTriggerManager.class);

        when(mockServices.getSqlProcessor()).thenReturn(mockProcessor);
        when(mockServices.getExecutor()).thenReturn(testExecutor);
        when(mockServices.getCloner()).thenReturn(mockCloner);
        when(mockServices.getTriggersManager()).thenReturn(mockTriggers);

        sqlTxManager = new XdStorageSQLTransactionManager(mockServices);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testBeginSQLTransaction_HappyPath_ShouldCreateActiveSQLTransaction() {
        XdStorageSQLTransaction tx = sqlTxManager.beginTransaction(5000L);

        assertNotNull(tx);
        assertTrue(sqlTxManager.isTransactionAlive(tx));
        assertSame(tx, sqlTxManager.getCurrentTransaction());
        assertSame(tx, sqlTxManager.getTransaction(tx.getTransactionId()));
    }

    @Test
    public void testCommitSQLTransaction_HappyPath_ShouldInvokeSQLProcessor() throws Throwable {
        XdStorageSQLTransaction tx = sqlTxManager.beginTransaction(5000L);

        sqlTxManager.commitTransaction(tx);

        // Проверяем штатную последовательность вызовов трех фаз процессора
        verify(mockProcessor, times(1)).executeAlter(tx);
        verify(mockProcessor, times(2)).execute(tx); // 1-я и 2-я фаза коммита
        assertFalse(sqlTxManager.isTransactionAlive(tx));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testCommitTransaction_WithAlterPhaseError_ShouldTriggerRollback() throws Throwable {
        XdStorageSQLTransaction tx = sqlTxManager.beginTransaction(5000L);

        // Сымитируем сбой структуры на фазе alter
        doThrow(new RuntimeException("SQL Сбой структуры")).when(mockProcessor).executeAlter(tx);

        // Запускаем коммит. Менеджер перехватит сбой и запустит цепочку компенсации rollbackFailedCommit
        sqlTxManager.commitTransaction(tx);

        // ИСПРАВЛЕНИЕ: Проверяем, что из-за аварийного закрытия ресурсов в блоке catch
        // метод execute() был вызван ровно 1 раз внутри rollbackFailedCommit для очистки коннектов!
        verify(mockProcessor, times(1)).execute(tx);
        assertFalse(sqlTxManager.isTransactionAlive(tx));
    }

    @Test
    public void testCommitTransaction_WhenMarkedRollbackOnly_ShouldThrowException() {
        XdStorageSQLTransaction tx = sqlTxManager.beginTransaction(5000L);
        tx.markRollbackOnly();

        assertThrows(XdStorageRuntimeException.class, () -> sqlTxManager.commitTransaction(tx));

        testExecutor.shutdown();
    }
}
