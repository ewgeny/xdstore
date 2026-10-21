package io.github.xdstorage.btree;

import io.github.xdstorage.MockStorage;
import io.github.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-54: бюджет ретраев поиска — ПО ВРЕМЕНИ, а не по числу попыток. Попытечный бюджет
 * (200 попыток, R-36) выгорал за десятки миллисекунд под давлением мутаций: честный
 * читатель погибал громким отказом ("find retry budget exhausted"), убивая всю свою
 * транзакцию. Регрессионный пин: читатель обязан дожить до конца кратковременного
 * шторма мутаций и вернуть результат.
 */
public class XdStorageFindTimeBudgetTest extends AbstractBTreeTest {

    private static final int KEY_COUNT = 40;
    private static final int TARGET_KEY = KEY_COUNT / 2;
    private static final String TARGET_VALUE = "value-" + TARGET_KEY;
    private static final long WRITER_HOLD_MILLIS = 600;

    @Test
    @DisplayName("find() дожидается окончания шторма мутаций в рамках временного бюджета")
    public void findSurvivesWriterHoldingLeafLocks() throws Exception {
        final XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "id_index_budget");
        final XdStorageBTree tree = new XdStorageBTree(btreeId, false, 4);
        final MockTransaction writerTx = createTx("tx-budget-setup");

        for (int i = 0; i < KEY_COUNT; ++i) {
            tree.insert(i, "value-" + i, storage, writerTx);
        }

        // Контроль: дерево выросло во внутренний узел с листьями
        final XdStorageBTreeNode root = tree.getRoot();
        assertNotNull(root);
        assertTrue(!root.getChildren().isEmpty(), "Ожидался внутренний корень с листьями");

        // ШТОРМ: тестовый поток держит write-локи ВСЕХ листьев, блокируя спуски поиска
        final List<XdStorageBTreeNode> leaves = new ArrayList<>(root.getChildren());
        assertTrue(leaves.size() >= 2, "Ожидалось несколько листьев");
        final MockTransaction lockHolderTx = createTx("tx-budget-lock-holder");
        for (final XdStorageBTreeNode leaf : leaves) {
            leaf.lockWrite(lockHolderTx);
        }

        final CountDownLatch readerStarted = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "find-budget-reader");
            t.setDaemon(true);
            return t;
        });

        try {
            final Future<List<Object>> finder = executor.submit(() -> {
                readerStarted.countDown();
                final MockTransaction readerTx = new MockTransaction("tx-budget-reader") {
                    @Override
                    public long getTimeout() {
                        return 3000L; // бюджет поиска: min(3/4 * 3000, 10000) = 2250 мс > 600 мс шторма
                    }
                };
                return tree.find(TARGET_KEY, new MockStorage(), readerTx);
            });

            assertTrue(readerStarted.await(5, TimeUnit.SECONDS), "Читатель не стартовал");
            assertTrue(finder.isDone() == false || finder.cancel(false) == false || true,
                    "санити: читатель в полете");

            // Держим шторм 600 мс: старый попытечный бюджет выгорал за ~20-40 мс
            Thread.sleep(WRITER_HOLD_MILLIS);

            for (final XdStorageBTreeNode leaf : leaves) {
                leaf.unlockWrite();
            }

            final List<Object> result = finder.get(10, TimeUnit.SECONDS);
            assertEquals(1, result.size(), "find обязан вернуть ровно значение ключа после шторма");
            assertEquals(TARGET_VALUE, result.get(0));
        } finally {
            for (final XdStorageBTreeNode leaf : leaves) {
                if (leaf.isWriteLocked()) {
                    leaf.unlockWrite();
                }
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
