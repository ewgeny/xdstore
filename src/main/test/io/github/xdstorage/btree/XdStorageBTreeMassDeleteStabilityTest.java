package io.github.xdstorage.btree;

import io.github.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Стресс-тесты МАССОВОГО УДАЛЕНИЯ: стабильность B+ Дерева в один поток
 * при больших N и разных порядках удаления. Это сценарий каскадной очистки
 * хранилища (afterEach-пурж на 1250+ объектах), где join/relink-каскады рвали
 * цепочку листьев и теряли ключи ("does not exists" при delete).
 *
 * Верификатор запускается на КОНТРОЛЬНЫХ ТОЧКАХ (каждые checkpointEvery
 * удалений) и в конце: точное множество ключей, сортировка цепочки,
 * целостность chain-vs-root, children == keys + 1, parent-указатели,
 * глубина листьев.
 */
@DisplayName("Массовое удаление: стабильность B+ Дерева в один поток")
public class XdStorageBTreeMassDeleteStabilityTest extends AbstractBTreeTest {

    // =========================================================================
    // Глубокий верификатор
    // =========================================================================

    private void deepVerify(final XdStorageBTree tree, final int t,
                            final Set<Integer> expected, final String context) {
        final List<Integer> inOrder = new ArrayList<>();
        final Set<Object> leavesViaRoot = new LinkedHashSet<>();
        final int[] leafDepth = {-1};

        if (tree.getRoot() != null) {
            descend(tree.getRoot(), 0, inOrder, leavesViaRoot, leafDepth, context, new HashSet<>());
        }

        // Цепочка листьев от firstLeaf
        final Set<Object> leavesViaChain = new LinkedHashSet<>();
        XdStorageBTreeNode cur = tree.getFirstLeaf();
        int guard = 0;
        Integer prev = null;
        while (cur != null) {
            assertTrue(leavesViaChain.add(System.identityHashCode(cur)),
                    context + ": цикл цепочки листьев");
            for (final Comparable k : cur.getKeys()) {
                final Integer ik = (Integer) k;
                assertTrue(prev == null || prev.compareTo(ik) <= 0,
                        context + ": сортировка цепочки нарушена " + prev + " > " + ik);
                inOrder.add(ik);
                prev = ik;
            }
            cur = cur.getNextTreeNodeOnThisLevel();
            assertTrue(++guard < 200000, context + ": бесконечная цепочка");
        }

        // Точное множество ключей
        final Set<Integer> actual = new TreeSet<>(inOrder);
        assertEquals(expected.size(), actual.size(), context + ": размер дерева");
        for (final Integer k : expected) {
            assertTrue(actual.contains(k), context + ": потерян ключ " + k);
        }

        // Цепочка vs корень
        assertEquals(leavesViaRoot, leavesViaChain,
                context + ": рассинхрон корня и цепочки листьев");

        // children == keys + 1
        if (tree.getRoot() != null) {
            checkShape(tree.getRoot(), t, true, context, new HashSet<>());
        }
    }

    private void descend(final XdStorageBTreeNode node, final int depth, final List<Integer> inOrder,
                         final Set<Object> leaves, final int[] leafDepth,
                         final String context, final Set<Object> visited) {
        assertTrue(visited.add(System.identityHashCode(node)), context + ": дубль узла при спуске");
        if (node.getChildren().isEmpty()) {
            assertEquals(node.getKeys().size(), node.getObjects().size(), context + ": keys != objects");
            if (leafDepth[0] == -1) leafDepth[0] = depth;
            assertEquals(leafDepth[0], depth, context + ": листья на разных глубинах");
            assertTrue(leaves.add(System.identityHashCode(node)));
            return;
        }
        assertEquals(node.getKeys().size() + 1, node.getChildren().size(),
                context + ": children != keys + 1 у узла " + node.getId());
        for (final XdStorageBTreeNode child : node.getChildren()) {
            assertNotNull(child, context + ": null-ребенок у узла " + node.getId());
            assertSame(node, child.getParent(), context + ": parent-указатель ребенка неверен");
            descend(child, depth + 1, inOrder, leaves, leafDepth, context, visited);
        }
    }

    private void checkShape(final XdStorageBTreeNode node, final int t, final boolean isRoot,
                            final String context, final Set<Object> visited) {
        if (node.getChildren().isEmpty()) {
            assertTrue(node.getKeys().size() <= 2 * t - 1, context + ": переполненный лист");
            return;
        }
        assertEquals(node.getKeys().size() + 1, node.getChildren().size(), context + ": children != keys+1");
        if (!isRoot) {
            assertTrue(node.getKeys().size() >= t - 1, context + ": недозаполненная внутренняя нода");
        }
        for (final XdStorageBTreeNode child : node.getChildren()) {
            checkShape(child, t, false, context, visited);
        }
    }

    // =========================================================================
    // Параметризованный прогон массового удаления
    // =========================================================================

    private void runMassDelete(final int t, final int n, final String order, final long seed,
                               final int checkpointEvery) throws Exception {
        final MockTransaction tx = createTx("tx-mass");
        final XdStorageBTree tree = new XdStorageBTree(
                new XdStorageBTreeId(Integer.class, "mass_t" + t), false, t);

        // Вставка по возрастанию (как генератор БД выдает монотонные id)
        final Set<Integer> live = new TreeSet<>();
        for (int i = 1; i <= n; i++) {
            tree.insert(i, "v-" + i, storage, tx);
            live.add(i);
        }

        // Порядок удаления
        final List<Integer> deleteOrder = new ArrayList<>(live);
        switch (order) {
            case "asc": break;
            case "desc": Collections.reverse(deleteOrder); break;
            case "random": Collections.shuffle(deleteOrder, new Random(seed)); break;
            case "evens-first": {
                final List<Integer> evens = new ArrayList<>(), odds = new ArrayList<>();
                for (final int k : deleteOrder) (k % 2 == 0 ? evens : odds).add(k);
                evens.addAll(odds);
                deleteOrder.clear();
                deleteOrder.addAll(evens);
                break;
            }
            default: throw new IllegalArgumentException(order);
        }

        // Массовое удаление с контрольными точками верификации
        int op = 0;
        for (final int k : deleteOrder) {
            tree.delete(k, storage, tx);
            live.remove(k);
            op++;
            if (op % checkpointEvery == 0 || op == deleteOrder.size()) {
                deepVerify(tree, t, live, order + " t=" + t + " N=" + n + " op#" + op + " key=" + k);
            }
        }
        assertEquals(0, live.size());
    }

    // =========================================================================
    // Точная модель каскадного пуржа: t=20, N=1250 (как afterEach на планетах)
    // =========================================================================

    @Test
    @DisplayName("t=20 N=1250: удаление по возрастанию (каскадный пурж)")
    public void massDelete1250Ascending() throws Exception {
        runMassDelete(20, 1250, "asc", 0, 50);
    }

    @Test
    @DisplayName("t=20 N=1250: удаление по убыванию")
    public void massDelete1250Descending() throws Exception {
        runMassDelete(20, 1250, "desc", 0, 50);
    }

    @Test
    @DisplayName("t=20 N=1250: удаление в случайном порядке (3 seed'а)")
    public void massDelete1250Random() throws Exception {
        for (final long seed : new long[]{1, 2, 3}) {
            runMassDelete(20, 1250, "random", seed, 50);
        }
    }

    @Test
    @DisplayName("t=20 N=1250: удаление четных id, затем нечетных")
    public void massDelete1250EvensFirst() throws Exception {
        runMassDelete(20, 1250, "evens-first", 0, 50);
    }

    // =========================================================================
    // Сетка факторов ветвления на среднем N
    // =========================================================================

    @Test
    @DisplayName("Сетка t=2/3/5/10/50 на N=300: случайное удаление")
    public void branchingFactorGrid() throws Exception {
        for (final int t : new int[]{2, 3, 5, 10, 50}) {
            runMassDelete(t, 300, "random", 77, 25);
        }
    }

    // =========================================================================
    // Чередование вставка/удаление волнами (t=20)
    // =========================================================================

    @Test
    @DisplayName("t=20: чередование волн вставок и удалений с финальным пуржем")
    public void interleavedWaves() throws Exception {
        final MockTransaction tx = createTx("tx-wave");
        final XdStorageBTree tree = new XdStorageBTree(
                new XdStorageBTreeId(Integer.class, "mass_wave"), false, 20);
        final Random rnd = new Random(11);
        final Set<Integer> live = new TreeSet<>();

        int checkpoint = 0;
        for (int wave = 0; wave < 12; wave++) {
            for (int i = 0; i < 50; i++) {
                int k = rnd.nextInt(2000);
                while (live.contains(k)) k = rnd.nextInt(2000);
                tree.insert(k, "v", storage, tx);
                live.add(k);
            }
            final List<Integer> snapshot = new ArrayList<>(live);
            Collections.shuffle(snapshot, rnd);
            final int toDelete = snapshot.size() / 3;
            for (int i = 0; i < toDelete; i++) {
                final int k = snapshot.get(i);
                tree.delete(k, storage, tx);
                live.remove(k);
                if (++checkpoint % 40 == 0) {
                    deepVerify(tree, 20, live, "wave=" + wave + " op#" + checkpoint);
                }
            }
        }
        deepVerify(tree, 20, live, "final");
        // Полная очистка финального состояния
        for (final int k : new ArrayList<>(live)) {
            tree.delete(k, storage, tx);
        }
        deepVerify(tree, 20, new TreeSet<>(), "after-final-purge");
    }
}
