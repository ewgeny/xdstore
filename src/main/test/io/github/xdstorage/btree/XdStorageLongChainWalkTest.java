package io.github.xdstorage.btree;

import io.github.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-59: обход цепочки листьев (find/read на multiple-деревьях) ИТЕРАЦИОНЕН.
 * Прежняя рекурсия по nextTreeNodeOnThisLevel давала ОДИН КАДР СТЕКА НА ЛИСТ:
 * multiple-дерево после десятков тысяч вставок накапливало тысячи листьев, и
 * find отсутствующего ключа за пределами диапазона (полный обход цепи) падал
 * StackOverflowError под нагрузкой полного сьюта (семейство XdStorageBTreeStress).
 * Регрессионный пин: длинная цепь обязана обходиться без переполнения стека.
 */
public class XdStorageLongChainWalkTest extends AbstractBTreeTest {

    @Test
    @DisplayName("Полный обход цепочки из тысяч листьев не переполняет стек")
    public void longChainWalkDoesNotOverflowStack() throws Exception {
        final XdStorageBTreeId treeId = new XdStorageBTreeId(Object.class, "id_index_long_chain");
        final XdStorageBTree tree = new XdStorageBTree(treeId, true, 3);
        final MockTransaction tx = createTx("tx-long-chain");

        final int keys = 30_000;
        for (int i = 0; i < keys; ++i) {
            tree.insert(i, "v" + i, storage, tx);
        }

        // Контроль масштаба: тысячи листьев (t=3 -> 2..5 ключей на лист),
        // именно такая глубина рвала стек рекурсией
        int leaves = 0;
        XdStorageBTreeNode node = tree.getFirstLeaf();
        assertNotNull(node);
        while (node != null) {
            ++leaves;
            node = node.getNextTreeNodeOnThisLevel();
        }
        assertTrue(leaves > 3000, "Ожидалась длинная цепь (>3000 листьев), было: " + leaves);

        // Поиск выполняется в потоке с ОГРАНИЧЕННЫМ стеком (512 КБ, как у пулов
        // ExecutorService): рекурсия по цепи рвала именно такие стеки. Прежняя
        // рекурсия (кадр на лист) падала здесь StackOverflowError детерминированно.
        final Throwable[] failure = new Throwable[1];
        final List<List<Object>> results = new java.util.ArrayList<>();
        final MockTransaction readerTx = createTx("tx-long-chain-reader");
        final Thread walker = new Thread(null, () -> {
            try {
                results.add(tree.find(10_000_000, storage, readerTx)); // полный обход цепи (мимо диапазона)
                results.add(tree.find(0, storage, readerTx));
                results.add(tree.find(keys - 1, storage, readerTx));
            } catch (final Throwable t) {
                failure[0] = t;
            }
        }, "long-chain-walker", 512 * 1024);
        walker.start();
        walker.join(120_000);
        assertTrue(!walker.isAlive(), "Обход цепи завис");
        if (failure[0] != null) {
            throw new AssertionError("R-59: обход длинной цепи обязан быть итерационным (без SOE)", failure[0]);
        }

        assertTrue(results.get(0).isEmpty(), "Отсутствующий ключ обязан вернуть пусто");
        assertEquals(1, results.get(1).size());
        assertEquals("v0", results.get(1).get(0));
        assertEquals(1, results.get(2).size());
        assertEquals("v" + (keys - 1), results.get(2).get(0));
    }

    private static void assertEquals(final int expected, final int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertEquals(final Object expected, final Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
