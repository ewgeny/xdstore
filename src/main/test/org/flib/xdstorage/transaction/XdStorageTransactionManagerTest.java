package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.factories.XdStorageSmartCloner;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageTransactionManager.
 */
public class XdStorageTransactionManagerTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageTransactionManager txManager;
    private ExecutorService testExecutor;

    @BeforeEach
    public void setUp() {
        // Создаем моки для сервис-локатора и внутренних утилит, которые дергает менеджер при коммите/откате
        mockServices = mock(XdStorageServicesLocator.class);
        testExecutor = Executors.newSingleThreadExecutor();
        XdStorageSmartCloner mockCloner = mock(XdStorageSmartCloner.class);
        XdStorageTriggerManager mockTriggers = mock(XdStorageTriggerManager.class);

        when(mockServices.getExecutor()).thenReturn(testExecutor);
        when(mockServices.getCloner()).thenReturn(mockCloner);
        when(mockServices.getTriggersManager()).thenReturn(mockTriggers);

        txManager = new XdStorageTransactionManager(mockServices);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testBeginTransaction_HappyPath_ShouldCreateActiveTransaction() {
        // Создаем базовую транзакцию с тайм-аутом 5000мс
        XdStorageTransaction tx = txManager.beginTransaction(5000L);

        assertNotNull(tx);
        assertNotNull(tx.getTransactionId());
        assertEquals(5000L, tx.getTimeout());
        assertTrue(txManager.isTransactionAlive(tx));

        // Проверяем, что менеджер правильно привязал транзакцию к текущему воркер-потоку jvm
        assertSame(tx, txManager.getCurrentTransaction());
        assertSame(tx, txManager.getTransaction(tx.getTransactionId()));
    }

    @Test
    public void testNestedTransactions_HappyPath_ShouldSuspendParent() {
        // 1. Стартуем глобальную родительскую транзакцию
        XdStorageTransaction parentTx = txManager.beginTransaction(10000L);

        // 2. Стартуем вложенную подтранзакцию в том же потоке
        XdStorageTransaction childTx = txManager.beginTransaction(5000L);

        assertNotNull(childTx);
        assertNotEquals(parentTx.getTransactionId(), childTx.getTransactionId());
        assertSame(parentTx, childTx.getGlobalTransaction());

        // Проверяем, что текущей активной транзакцией потока стала дочерняя подтранзакция
        assertSame(childTx, txManager.getCurrentTransaction());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testCommitTransaction_WhenMarkedRollbackOnly_ShouldThrowException() {
        XdStorageTransaction tx = txManager.beginTransaction(5000L);

        // Имитируем падение или явную отметку "только откат"
        tx.markRollbackOnly();
        assertTrue(tx.isRollbackOnly());

        // Граничное условие: попытка закоммитить rollbackOnly транзакцию обязана выкинуть XdStorageRuntimeException
        assertThrows(XdStorageRuntimeException.class, () -> txManager.commitTransaction(tx));
    }

    @Test
    public void testIsTransactionAlive_WithRollbackOnly_ShouldReturnFalse() {
        XdStorageTransaction tx = txManager.beginTransaction(5000L);
        assertTrue(txManager.isTransactionAlive(tx));

        tx.markRollbackOnly();

        // Граничное условие: транзакция с флагом rollbackOnly больше не должна считаться живой СУБД
        assertFalse(txManager.isTransactionAlive(tx));
    }

    @Test
    public void testThreadIsolation_ParallelThreads_ShouldHaveIndependentTransactions() throws Exception {
        // Граничное условие: проверка полной потокоизоляции (Thread-Safety) менеджера транзакций.
        // Запускаем транзакцию в параллельном потоке и проверяем, что в текущем главном потоке она не видна.
        XdStorageTransaction mainThreadTx = txManager.beginTransaction(3000L);

        Future<XdStorageTransaction> future = testExecutor.submit(() -> txManager.beginTransaction(4000L));
        XdStorageTransaction parallelThreadTx = future.get();

        assertNotNull(parallelThreadTx);
        assertNotEquals(mainThreadTx.getTransactionId(), parallelThreadTx.getTransactionId());

        // Проверяем, что главный поток видит строго свою транзакцию, а не параллельную
        assertSame(mainThreadTx, txManager.getCurrentTransaction());

        testExecutor.shutdown();
    }
}
