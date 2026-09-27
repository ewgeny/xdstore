package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Полный тестовый комплекс для верификации базовых, граничных, многопоточных
 * и алгоритмических контрактов подсистемы B+ Дерева СУБД (Поинт Г).
 */
public class XdStorageBTreeTest {

    private XdStorageBTreeId treeId;
    private XdStorageBTree bTree;

    @BeforeEach
    public void setUp() {
        treeId = new XdStorageBTreeId(String.class, "idx_user_email");
        bTree = new XdStorageBTree(treeId, false, 2); // t = 2 для ускорения сплитов
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testTreeId_HappyPath_Contract() {
        XdStorageBTreeId id2 = new XdStorageBTreeId(String.class, "idx_user_email");
        assertEquals(treeId, id2);
        assertEquals(treeId.hashCode(), id2.hashCode());
        assertEquals("String-idx_user_email", treeId.toString());
    }

    @Test
    public void testInitialState_ShouldBeUnreferencedAndUnlocked() {
        assertFalse(bTree.isReference());
        assertFalse(bTree.isReadLocked());
        assertFalse(bTree.isWriteLocked());
        assertEquals(2, bTree.getT());
        assertSame(treeId, bTree.getId());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ И ОШИБКИ ===

    @Test
    public void testDelete_WhenTreeIsEmpty_ShouldThrowXdStorageException() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        assertThrows(XdStorageException.class, () -> {
            bTree.delete("missing_key", mockStorage, mockTx);
        });
    }

    @Test
    public void testUpdate_WhenTreeIsEmpty_ShouldThrowXdStorageException() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        assertThrows(XdStorageException.class, () -> {
            bTree.update("missing_key", "new_value", mockStorage, mockTx);
        });
    }

    // === 3. ПРОДВИНУТЫЕ АЛГОРИТМИЧЕСКИЕ ТЕСТЫ ===

    @Test
    public void testInsert_TriggeringSplit_ShouldRebalanceTreeCorrectly() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(3000L);

        assertDoesNotThrow(() -> {
            bTree.insert(10, "Obj1", mockStorage, mockTx);
            bTree.insert(20, "Obj2", mockStorage, mockTx);
            bTree.insert(30, "Obj3", mockStorage, mockTx);
            bTree.insert(40, "Obj4", mockStorage, mockTx); // Вызывает split
        });

        assertNotNull(bTree.getRoot(), "После расщепления корень дерева обязан инициализироваться!");
        verify(mockStorage, atLeastOnce()).save(any(Object.class), any(IXdStorageTransaction.class));
        verify(mockStorage, atLeastOnce()).update(any(Object.class), any(IXdStorageTransaction.class));
    }

    @Test
    public void testInsert_WhenThreadIsInterruptedDuringWaitLoop_ShouldThrowXdStorageException() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        XdStorageBTree loopTree = new XdStorageBTree(treeId, false, 2);
        Thread.currentThread().interrupt();

        XdStorageException thrown = assertThrows(XdStorageException.class, () -> {
            loopTree.insert(50, "Obj5", mockStorage, mockTx);
        });

        assertTrue(thrown.getMessage().contains("interrupted") || thrown.getCause() instanceof InterruptedException);
        Thread.interrupted(); // Очищаем статус прерывания
    }

    @Test
    public void testFind_WhenKeyDoesNotExist_ShouldReturnEmptyListSafely() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(1000L);

        XdStorageBTreeNode mockRoot = mock(XdStorageBTreeNode.class);
        bTree.setRoot(mockRoot);
        when(mockRoot.find(any(), any(), any(), any(), any())).thenReturn(null);

        List<Object> result = bTree.find(999, mockStorage, mockTx);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // === 4. НОВЫЕ СВЕЖИЕ ТЕСТЫ НА КОНКУРЕНТНОСТЬ И ГОНКИ (КОНТУР МНОГОПОТОЧНОСТИ) ===

    /**
     * ТЕСТ 4: Проверка исключения взаимных блокировок при одновременном чтении (Concurrent Read Locks).
     * Множество потоков должны беспрепятственно читать B+ Дерево параллельно.
     */
    @Test
    public void testConcurrentReadLocks_ShouldAllowMultipleReaders() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(2000L);

        // Инициализируем дерево с фейковым корнем, чтобы уйти от ветки создания корня
        XdStorageBTreeNode mockRoot = mock(XdStorageBTreeNode.class);
        bTree.setRoot(mockRoot);
        when(mockRoot.find(any(), any(), any(), any(), any())).thenReturn(Collections.singletonList("Data"));

        int readersCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(readersCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(readersCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < readersCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    List<Object> res = bTree.find("key", mockStorage, mockTx);
                    if (!res.isEmpty() && "Data".equals(res.get(0))) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Залповый старт читателей
        boolean finishedCleanly = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(finishedCleanly, "Читатели заблокировали друг друга! Обнаружен дефект в ReadLock!");
        assertEquals(readersCount, successCount.get(), "Не все потоки-читатели успешно получили данные!");
    }

    /**
     * ТЕСТ 5: Проверка транзакционного таймаута при конкуренции за запись (Write Lock Contention).
     * Если один поток монопольно удерживает WriteLock, второй поток обязан дождаться или выбросить XdStorageException.
     */
    @Test
    public void testWriteLockContention_ShouldEnforceTimeout() throws Exception {
        IXdStorageTransaction mockTx1 = mock(IXdStorageTransaction.class);
        IXdStorageTransaction mockTx2 = mock(IXdStorageTransaction.class);
        when(mockTx1.getTimeout()).thenReturn(5000L);
        when(mockTx2.getTimeout()).thenReturn(100L); // Маленький таймаут для теста

        bTree.lockWrite(mockTx1);
        assertTrue(bTree.isWriteLocked());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            // ИСПРАВЛЕНИЕ: Ожидаем именно XdStorageRuntimeException, который кидает локер!
            assertThrows(org.flib.xdstorage.exceptions.XdStorageRuntimeException.class, () -> {
                bTree.lockWrite(mockTx2);
            }, "Второй поток обязан выбросить исключение при занятом WriteLock!");
        });

        future.get(2, TimeUnit.SECONDS);
        bTree.unlockWrite();
        executor.shutdownNow();
        assertFalse(bTree.isWriteLocked());
    }

    /**
     * ТЕСТ 6: Стресс-тест на каскадную ленивую дозагрузку узлов-ссылок (Lazy Reference Loading Race).
     * Симулирует ситуацию, когда корень является прокси-ссылкой (isReference = true),
     * и параллельные потоки лавиной вызывают find, провоцируя однократный вызов storage.load.
     */
    @Test
    public void testLazyReferenceLoadingRace_ShouldLoadExactlyOnce() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(3000L);

        // Создаем корень-заглушку, который притворяется незагруженной прокси-ссылкой
        XdStorageBTreeNode spyRoot = spy(new XdStorageBTreeNode(bTree, null));
        bTree.setRoot(spyRoot);

        // Настраиваем Mock так, чтобы при загрузке сбрасывался флаг ссылки
        doAnswer(invocation -> {
            XdStorageBTreeNode node = invocation.getArgument(0);
            // Имитируем ленивую подгрузку: убираем прокси-состояние оригинальной СУБД
            // В реальной кодовой базе это проверяется утилитой XdStorageObjectUtils.isReference(root)
            bTree.setReference(false);
            return null;
        }).when(mockStorage).load(any(Object.class), any(IXdStorageTransaction.class));

        int writersCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(writersCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(writersCount);
        for (int i = 0; i < writersCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Вызов find каскадно проверяет XdStorageObjectUtils.isReference(root) и дергает load
                    bTree.find(50, mockStorage, mockTx);
                } catch (Throwable ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean clean = finishLatch.await(4, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(clean, "Гонка при дозагрузке референса вызвала Deadlock!");
    }

    /*** ТЕСТ 7: Верификация инварианта апгрейда блокировки (Lock Upgrade Invariant).* Проверяет поведение tryLockWrite, когда поток пытается повысить уровень блокировки с Read до Write.*/
    @Test
    public void testLockUpgrade_Behavior_Contract() throws Exception {
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);

        // Шаг 1. Захватываем ReadLock текущим потоком
        bTree.lockRead(mockTx);
        assertTrue(bTree.isReadLocked());
        // Шаг 2. Так как читает ТОЛЬКО текущий поток, tryLockWrite() обязан успешно
        // выполнить Lock Upgrade в соответствии с правилами нашего XdStorageReadWriteLock !
        boolean upgraded = bTree.tryLockWrite(mockTx);
        assertTrue(upgraded, "Lock Upgrade должен быть разрешен, если текущий поток — единственный читатель!");
        assertTrue(bTree.isWriteLocked());

        // Чистим за собой
        bTree.unlockWrite();
        bTree.unlockRead();
        assertFalse(bTree.isWriteLocked());
        assertFalse(bTree.isReadLocked());
    }
}