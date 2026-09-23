package org.flib.xdstorage.lock;

import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых, реентерабельных и граничных условий класса XdStorageReadWriteLock.
 */
public class XdStorageReadWriteLockTest {

    private XdStorageReadWriteLock rwLock;

    @BeforeEach
    public void setUp() {
        rwLock = new XdStorageReadWriteLock();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь / Реентерабельность) ===

    @Test
    public void testInitialState_ShouldNotBeLocked() {
        assertFalse(rwLock.isReadLocked());
        assertFalse(rwLock.isWriteLocked());
    }

    @Test
    public void testTryLockRead_HappyPath_AndReentrancy() {
        // Первый захват на чтение
        assertTrue(rwLock.tryLockRead());
        assertTrue(rwLock.isReadLocked());
        assertFalse(rwLock.isWriteLocked());

        // Проверяем реентерабельность: повторный захват тем же потоком разрешен
        assertTrue(rwLock.tryLockRead());

        // Каскадное освобождение
        assertDoesNotThrow(() -> rwLock.unlockRead());
        assertTrue(rwLock.isReadLocked()); // еще удерживается первый лок

        assertDoesNotThrow(() -> rwLock.unlockRead());
        assertFalse(rwLock.isReadLocked()); // полностью освобожден
    }

    @Test
    public void testTryLockWrite_HappyPath_AndReentrancy() {
        // Первый захват на запись
        assertTrue(rwLock.tryLockWrite());
        assertTrue(rwLock.isWriteLocked());
        assertFalse(rwLock.isReadLocked());

        // Проверяем реентерабельность записи
        assertTrue(rwLock.tryLockWrite());

        assertDoesNotThrow(() -> rwLock.unlockWrite());
        assertTrue(rwLock.isWriteLocked());

        assertDoesNotThrow(() -> rwLock.unlockWrite());
        assertFalse(rwLock.isWriteLocked());
    }

    @Test
    public void testLockUpgrade_ReadThenWrite_ShouldBeAllowedForSameThread() {
        // Инвариант ядра СУБД: Поток, удерживающий Read-лок, может запросить Write-лок
        assertTrue(rwLock.tryLockRead());
        assertTrue(rwLock.tryLockWrite());

        assertTrue(rwLock.isReadLocked());
        assertTrue(rwLock.isWriteLocked());

        assertDoesNotThrow(() -> rwLock.unlockWrite());
        assertDoesNotThrow(() -> rwLock.unlockRead());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ (Edge Cases) ===

    @Test
    public void testUnlockWrite_WhenNotLocked_ShouldThrowIllegalMonitorStateException() {
        // Граничное условие: Попытка снять Write-блокировку, которая не захватывалась текущим потоком
        assertThrows(IllegalMonitorStateException.class, () -> rwLock.unlockWrite());
    }

    @Test
    public void testUnlockRead_WhenNotLocked_ShouldThrowIllegalMonitorStateException() {
        // Граничное условие: Попытка снять Read-блокировку без предварительного захвата
        assertThrows(IllegalMonitorStateException.class, () -> rwLock.unlockRead());
    }

    @Test
    public void testLockRead_HappyPath_WithTransactionTimeout() throws InterruptedException {
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        // Проверяем стандартный синхронный метод блокировки по тайм-ауту
        assertDoesNotThrow(() -> rwLock.lockRead(mockTx));
        assertTrue(rwLock.isReadLocked());

        assertDoesNotThrow(() -> rwLock.unlockRead());
    }

    @Test
    public void testLockWrite_HappyPath_WithTransactionTimeout() throws InterruptedException {
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        // Проверяем стандартный синхронный метод эксклюзивной блокировки
        assertDoesNotThrow(() -> rwLock.lockWrite(mockTx));
        assertTrue(rwLock.isWriteLocked());

        assertDoesNotThrow(() -> rwLock.unlockWrite());
    }
}
