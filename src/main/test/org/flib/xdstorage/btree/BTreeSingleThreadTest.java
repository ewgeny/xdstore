package org.flib.xdstorage.btree;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("1. Функциональное однопоточное тестирование операций B+ Дерева")
public class BTreeSingleThreadTest extends AbstractBTreeTest {

    @Test
    @DisplayName("Последовательная вставка 10 элементов (Happy Path) и верификация find")
    public void singleInsertTest1() throws XdStorageException, XdStorageConnectionException {
        MockTransaction tx = createTx("tx-single-1");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 10; ++i) {
            tree.insert(i, new Object(), storage, tx);
        }

        for (int i = 0; i < 10; ++i) {
            List<Object> result = tree.find(i, storage, tx);
            assertEquals(1, result.size(), "Ключ потерян при однопоточной вставке: " + i);
        }
    }

    @Test
    @DisplayName("Последовательная вставка 30 элементов с каскадными дисковыми сплитами страниц")
    public void singleInsertTest2() throws XdStorageException, XdStorageConnectionException {
        MockTransaction tx = createTx("tx-single-2");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(i, new Object(), storage, tx);
        }

        for (int i = 0; i < 30; ++i) {
            List<Object> result = tree.find(i, storage, tx);
            assertEquals(1, result.size());
        }
    }

    @Test
    @DisplayName("Последовательное удаление 10 элементов (Сжатие ярусов без остатка)")
    public void singleDeleteTest1() throws XdStorageException, XdStorageConnectionException {
        MockTransaction tx = createTx("tx-single-del-1");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 10; ++i) {
            tree.insert(i, new Object(), storage, tx);
        }

        for (int i = 0; i < 10; ++i) {
            tree.delete(i, storage, tx);
            List<Object> result = tree.find(i, storage, tx);
            assertTrue(result.isEmpty(), "Ключ не стерт из листа после удаления: " + i);
        }
    }

    @Test
    @DisplayName("Последовательное удаление 30 элементов с каскадными merge-слияниями нод")
    public void singleDeleteTest2() throws XdStorageException, XdStorageConnectionException {
        MockTransaction tx = createTx("tx-single-del-2");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(i, new Object(), storage, tx);
        }

        for (int i = 0; i < 30; ++i) {
            tree.delete(i, storage, tx);
            List<Object> result = tree.find(i, storage, tx);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("Валидация сохранности правых соседей при последовательном выкашивании левого крыла")
    public void singleDeleteTest3() throws XdStorageException, XdStorageConnectionException {
        MockTransaction tx = createTx("tx-single-del-3");
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(i, new Object(), storage, tx);
        }

        for (int i = 0; i < 30; ++i) {
            tree.delete(i, storage, tx);
            for (int j = i + 1; j < 30; ++j) {
                List<Object> result = tree.find(j, storage, tx);
                assertEquals(1, result.size(), "Слияние левых страниц деструктивно задело правого соседа: " + j);
            }
        }
    }
}
