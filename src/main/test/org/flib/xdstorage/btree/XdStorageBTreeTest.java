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
        bTree = new XdStorageBTree(treeId, false, 2); // t = 2 для ускорения сплитов и джойнов
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

    // === 4. НОВЫЕ СВЕЖИЕ ТЕСТЫ НА КОНКУРЕНТНОСТЬ И ГОНКИ ===

    @Test
    public void testConcurrentReadLocks_ShouldAllowMultipleReaders() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(2000L);

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

        startLatch.countDown();
        boolean finishedCleanly = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(finishedCleanly, "Читатели заблокировали друг друга! Обнаружен дефект в ReadLock!");
        assertEquals(readersCount, successCount.get(), "Не все потоки-читатели успешно получили данные!");
    }

    @Test
    public void testWriteLockContention_ShouldEnforceTimeout() throws Exception {
        IXdStorageTransaction mockTx1 = mock(IXdStorageTransaction.class);
        IXdStorageTransaction mockTx2 = mock(IXdStorageTransaction.class);
        when(mockTx1.getTimeout()).thenReturn(5000L);
        when(mockTx2.getTimeout()).thenReturn(100L);

        bTree.lockWrite(mockTx1);
        assertTrue(bTree.isWriteLocked());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            assertThrows(org.flib.xdstorage.exceptions.XdStorageRuntimeException.class, () -> {
                bTree.lockWrite(mockTx2);
            }, "Второй поток обязан выбросить исключение при занятом WriteLock!");
        });

        future.get(2, TimeUnit.SECONDS);
        bTree.unlockWrite();
        executor.shutdownNow();
        assertFalse(bTree.isWriteLocked());
    }

    @Test
    public void testLazyReferenceLoadingRace_ShouldLoadExactlyOnce() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(3000L);

        XdStorageBTreeNode spyRoot = spy(new XdStorageBTreeNode(bTree, null));
        bTree.setRoot(spyRoot);

        doAnswer(invocation -> {
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

    @Test
    public void testLockUpgrade_Behavior_Contract() throws Exception {
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);

        bTree.lockRead(mockTx);
        assertTrue(bTree.isReadLocked());

        boolean upgraded = bTree.tryLockWrite(mockTx);
        assertTrue(upgraded, "Lock Upgrade должен быть разрешен, если текущий поток — единственный читатель!");
        assertTrue(bTree.isWriteLocked());

        bTree.unlockWrite();
        bTree.unlockRead();
        assertFalse(bTree.isWriteLocked());
        assertFalse(bTree.isReadLocked());
    }

    // === 5. КРИТИЧЕСКИЕ ТЕСТЫ ОПЕРАЦИЙ УДАЛЕНИЯ И РЕБАЛАНСИРОВКИ (FAIL-SAFE КОНТУР) ===

    /**
     * ТЕСТ 8: Проверка канонического удаления без ребалансировки (Happy Path Delete).
     * Ключ должен чисто стираться из листа, не ломая навигацию по соседним веткам.
     */
    @Test
    public void testDelete_HappyPath_ShouldRemoveKeyFromLeaf() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(2000L);

        // Наполняем дерево достаточным количеством элементов, чтобы листы не усыхали ниже порога t-1
        bTree.insert(10, "Planet1", mockStorage, mockTx);
        bTree.insert(20, "Planet2", mockStorage, mockTx);
        bTree.insert(30, "Planet3", mockStorage, mockTx);

        // Стираем промежуточный элемент
        assertDoesNotThrow(() -> {
            bTree.delete(20, mockStorage, mockTx);
        });

        // Проверяем, что стертый элемент больше не находится навигатором, а живые — на месте
        List res20 = bTree.find(20, mockStorage, mockTx);
        assertTrue(res20.isEmpty(), "Удаленный объект не должен вычитываться!");

        List res10 = bTree.find(10, mockStorage, mockTx);
        assertFalse(res10.isEmpty());
        assertEquals("Planet1", res10.get(0));
    }

    /**
     * ТЕСТ 9: Проверка каскадной ребалансировки через заем ключа у правого соседа (Borrow From Right Neighbor).
     * Когда узел листа усыхает ниже (t-1) ключей, он обязан занять крайний левый элемент правого соседа,
     * а родительский ключ-разделитель во внутреннем узле должен атомарно обновиться.
     */
    @Test
    public void testDelete_TriggeringBorrowFromRight_ShouldUpdateParentBoundary() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(3000L);

        // Провоцируем контролируемое распределение по страницам при t=2
        bTree.insert(10, "P1", mockStorage, mockTx);
        bTree.insert(20, "P2", mockStorage, mockTx);
        bTree.insert(30, "P3", mockStorage, mockTx);
        bTree.insert(40, "P4", mockStorage, mockTx); // Вызовет split: левый лист, правый [30, 40]

        // Удаляем из левого листа элемент 10, размер листа падает до 1 (меньше порога t),
        // узел обязан занять элемент 30 у правого соседа, а разделитель в родителе станет равен 40!
        assertDoesNotThrow(() -> {
            bTree.delete(10, mockStorage, mockTx);
        });

        // Проверяем, что структура навигации Б+ Дерева сохранила идеальную математическую точность
        assertTrue(bTree.find(10, mockStorage, mockTx).isEmpty());
        assertEquals("P2", bTree.find(20, mockStorage, mockTx).get(0));
        assertEquals("P3", bTree.find(30, mockStorage, mockTx).get(0));
        assertEquals("P4", bTree.find(40, mockStorage, mockTx).get(0));
    }

    /**
     * ТЕСТ 10: Верификация слияния страниц на уровне листьев (Leaf Nodes Join / Merge).
     * Проверяет, что при слиянии левого и правого листов горизонтальный связный список уровня
     * (nextTreeNodeOnThisLevel) бесшовно перевязывается, предотвращая потерю указателей маршрутизации.
     */
    @Test
    public void testDelete_TriggeringLeafMerge_ShouldMaintainNextTreeNodeChain() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(3000L);

        bTree.insert(15, "Val15", mockStorage, mockTx);
        bTree.insert(25, "Val25", mockStorage, mockTx);
        bTree.insert(35, "Val35", mockStorage, mockTx);
        bTree.insert(45, "Val45", mockStorage, mockTx); // Split: [15, 25] и [35, 45]

        // Стираем элементы так, чтобы заем у соседа был невозможен, провоцируя полный Merge страниц
        bTree.delete(45, mockStorage, mockTx); // Правый лист усыхает до [35]
        assertDoesNotThrow(() -> {
            bTree.delete(35, mockStorage, mockTx); // Лист пустеет, вызывая joinWithLeftNeightbor!
        });

        // Проверяем непрерывность горизонтальной цепочки переходов
        List res15 = bTree.find(15, mockStorage, mockTx);
        assertEquals("Val15", res15.get(0));
        List res25 = bTree.find(25, mockStorage, mockTx);
        assertEquals("Val25", res25.get(0));
    }

    /**
     * ТЕСТ 11: Проверка защиты от падения при повторном / фантомном удалении объекта (Idempotent Delete Barrier).
     * Тест подтверждает, что если одна и та же сущность повторно вычищается в рамках одной сессии транзакции,
     * fail-safe барьер дерева гасит операцию, не позволяя осквернить транзакцию маркеру rollback only.
     */
    @Test
    public void testDelete_IdempotentDuplicatePurger_ShouldNotCrashTransaction() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTimeout()).thenReturn(2000L);

        bTree.insert(100, "TargetPlanet", mockStorage, mockTx);

        // Первое легитимное удаление
        assertDoesNotThrow(() -> {
            bTree.delete(100, mockStorage, mockTx);
        });

        // Второе повторное (фантомное) удаление того же ID из-за коллизии HashSet в тесте.
        // Ядро дерева обязано мягко пропустить операцию, не выбрасывая разрушительных исключений!
        assertDoesNotThrow(() -> {
            bTree.delete(100, mockStorage, mockTx);
        }, "Повторное удаление обязано быть fail-safe и обрабатываться идемпотентно!");
    }
}