package org.flib.xdstorage.btree;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("2. Высоконагруженное многопоточное стресс-тестирование ярусов B+ Дерева")
public class BTreeConcurrencyTest extends AbstractBTreeTest {

    @Test
    @DisplayName("Параллельная ForkJoin вставка 10 элементов на изолированных транзакциях")
    public void multithreadInsertTest1() throws XdStorageException, XdStorageConnectionException {
        MockTransaction txReader = createTx("tx-reader");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(10);
        for (int i = 0; i < 10; ++i) {
            identifiers.add(i);
        }

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-worker-insert-" + i);
                tree.insert(i, new Object(), storage, txWorker);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        assertTrue(exceptions.isEmpty(), "Зафиксированы падения в ForkJoin потоках вставки: " + exceptions);

        for (int i = 0; i < 10; ++i) {
            List<Object> result = tree.find(i, storage, txReader);
            assertEquals(1, result.size());
        }
    }

    @Test
    @DisplayName("Параллельная вставка 30 элементов (Сплиты под перекрестной нагрузкой)")
    public void multithreadInsertTest2() throws XdStorageException, XdStorageConnectionException {
        MockTransaction txReader = createTx("tx-reader");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(30);
        for (int i = 0; i < 30; ++i) {
            identifiers.add(i);
        }

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-worker-insert-heavy-" + i);
                tree.insert(i, new Object(), storage, txWorker);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        assertTrue(exceptions.isEmpty(), "Зафиксированы падения при каскадных сплитах под нагрузкой: " + exceptions);

        for (int i = 0; i < 30; ++i) {
            List<Object> result = tree.find(i, storage, txReader);
            assertEquals(1, result.size());
        }
    }

    @Test
    @DisplayName("Многопоточное изолированное удаление диапазона из 10 элементов")
    public void multithreadDeleteTest1() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(10);
        for (int i = 0; i < 10; ++i) {
            identifiers.add(i);
        }

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-worker-mixed-add-" + i);
                tree.insert(i, new Object(), storage, txWorker);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-worker-mixed-del-" + i);
                List<Object> result = tree.find(i, storage, txWorker);
                assertEquals(1, result.size());

                tree.delete(i, storage, txWorker);

                result = tree.find(i, storage, txWorker);
                assertTrue(result.isEmpty());
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        assertTrue(exceptions.isEmpty(), "Вылетели ошибки при многопоточном пурдже диапазона: " + exceptions);
    }

    @Test
    @DisplayName("Многопоточное каскадное удаление 40 элементов (Схлопывание под нагрузкой)")
    public void multithreadDeleteTest2() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(40);
        for (int i = 0; i < 40; ++i) {
            identifiers.add(i);
        }

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-heavy-add-" + i);
                tree.insert(i, new Object(), storage, txWorker);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        identifiers.parallelStream().forEach(i -> {
            try {
                MockTransaction txWorker = createTx("tx-heavy-del-" + i);
                List<Object> result = tree.find(i, storage, txWorker);
                assertEquals(1, result.size());

                tree.delete(i, storage, txWorker);
                result = tree.find(i, storage, txWorker);
                assertTrue(result.isEmpty());
            } catch (Exception e) {
                exceptions.add(e);
            }
        });
        assertTrue(exceptions.isEmpty(), "Зафиксированы коллизии ребалансировок при лавинном многопоточном сжатии: " + exceptions);
    }

    @Test
    @DisplayName("Ультра-стресс сценарий 1: 1000 параллельных ForkJoin вставок (Лавина сплитов)")
    public void testConcurrent_InsertHeavy_ShouldHandleMassSplits() {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(String.class, "btree_concurrent_insert");
        XdStorageBTree tree = new XdStorageBTree(btreeId, true, 10);

        final int count = 1000;
        final List<Integer> identifiers = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            identifiers.add(i + 1);
        }

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(key -> {
            try {
                MockTransaction txWorker = createTx("tx-mass-insert-" + key);
                tree.insert(key, "Value_" + key, storage, txWorker);
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        assertTrue(exceptions.isEmpty(), "Воркеры массовой вставки упали с ошибками: " + exceptions);

        MockTransaction txReader = createTx("tx-mass-insert-reader");
        for (int i = 1; i <= count; ++i) {
            try {
                List<Object> result = tree.find(i, storage, txReader);
                assertEquals(1, result.size());
                assertEquals("Value_" + i, result.get(0));
            } catch (Throwable t) {
                fail("Крах навигации find при валидации вставленного графа для ключа: " + i, t);
            }
        }
    }

    @Test
    @DisplayName("Ультра-стресс сценарий 2: 1000 параллельных ForkJoin удалений (Лавина схлопываний)")
    public void testConcurrent_DeleteHeavy_ShouldHandleMassCollapses() {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(String.class, "btree_concurrent_delete");
        XdStorageBTree tree = new XdStorageBTree(btreeId, true, 10);

        final int count = 1000;
        final List<Integer> identifiers = new ArrayList<>(count);

        MockTransaction txInit = createTx("tx-mass-delete-init");
        for (int i = 0; i < count; ++i) {
            int key = i + 1;
            identifiers.add(key);
            try {
                tree.insert(key, "Value_" + key, storage, txInit);
            } catch (XdStorageException | XdStorageConnectionException e) {
                fail("Ошибка предзаполнения дерева перед тестом удаления", e);
            }
        }

        Collections.shuffle(identifiers, new Random(42));
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        identifiers.parallelStream().forEach(key -> {
            try {
                MockTransaction txWorker = createTx("tx-mass-delete-" + key);
                tree.delete(key, storage, txWorker);
            } catch (Throwable t) {
                exceptions.add(t);
            }
        });

        assertTrue(exceptions.isEmpty(), "Воркеры массового удаления упали с ошибками: " + exceptions);

        MockTransaction txFinal = createTx("tx-mass-delete-final");
        for (int i = 1; i <= count; ++i) {
            try {
                List<Object> result = tree.find(i, storage, txFinal);
                assertTrue(result.isEmpty(), "Ключ остался в дереве после удаления: " + i);
            } catch (Throwable t) {
                fail("Крах навигации find в пустом дереве для ключа: " + i, t);
            }
        }
    }
}
