package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.trigger.IXdStorageTrigger;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * In-memory tests for {@link XdStorageBTree}: structure invariants, insert/find/delete/update
 * correctness (including duplicate keys split across leaves), leaf-chain traversal and
 * concurrent inserts. The storage keeps every object alive in a map, so nodes are never
 * turned into lazy references and the pure tree algorithms are exercised.
 */
public class XdStorageBTreeTest {

    private static final long TX_TIMEOUT = 5000L;

    private static final AtomicLong TX_COUNTER = new AtomicLong();

    private static final class TestTransaction implements IXdStorageTransaction {

        private final String id = "tx-" + TX_COUNTER.incrementAndGet();

        @Override
        public String getTransactionId() {
            return id;
        }

        @Override
        public String getTransactionThreadId() {
            return id;
        }

        @Override
        public long getTimeout() {
            return TX_TIMEOUT;
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void markRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }
    }

    private static final class InMemoryStorage implements IXdStorage {

        private final AtomicLong idGenerator = new AtomicLong(1);

        private final Map<Class<?>, Map<Object, Object>> tables = new ConcurrentHashMap<>();

        private Map<Object, Object> table(final Class<?> cl) {
            return tables.computeIfAbsent(cl, key -> new ConcurrentHashMap<>());
        }

        private Object idOf(final Object object) {
            final XdStorageObjectIdField idField = XdStorageObjectUtils.getClassInfo(object.getClass()).getIdField();
            return idField.get(object);
        }

        @Override
        public void save(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            Object id = idOf(object);
            if (id == null) {
                final XdStorageObjectIdField idField = XdStorageObjectUtils.getClassInfo(object.getClass()).getIdField();
                id = idGenerator.incrementAndGet();
                idField.set(object, id);
            }
            table(object.getClass()).put(id, object);
        }

        @Override
        public void update(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            table(object.getClass()).put(idOf(object), object);
        }

        @Override
        public void delete(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            table(reference.getClass()).remove(idOf(reference));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T load(final Class<T> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            return (T) table(cl).get(id);
        }

        @Override
        public void load(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            // everything is already materialized in memory
        }

        @Override
        public boolean has(final Class<?> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
            return table(cl).containsKey(id);
        }

        @Override
        public String getName() {
            return "in-memory-test-storage";
        }

        @Override
        public <T> void registerTrigger(final IXdStorageTrigger<T> trigger) {
        }

        @Override
        public IXdStorageTransaction beginTransaction(final long timeout) {
            return new TestTransaction();
        }

        @Override
        public IXdStorageTransaction beginTransaction() {
            return new TestTransaction();
        }

        @Override
        public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction, final long timeout) {
            return new TestTransaction();
        }

        @Override
        public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction) {
            return new TestTransaction();
        }

        @Override
        public void commitTransaction(final IXdStorageTransaction transaction) {
        }

        @Override
        public void rollbackTransaction(final IXdStorageTransaction transaction) {
        }

        @Override
        public void save(final Object object) {
        }

        @Override
        public void save(final Collection<?> objects) {
        }

        @Override
        public void save(final Collection<?> objects, final IXdStorageTransaction transaction) {
        }

        @Override
        public boolean has(final Class<?> cl, final Object id) {
            return table(cl).containsKey(id);
        }

        @Override
        public void load(final Object reference) {
        }

        @Override
        public <T> T load(final Class<T> cl, final Object id) {
            return null;
        }

        @Override
        public void load(final Collection<?> references) {
        }

        @Override
        public void load(final Collection<?> references, final IXdStorageTransaction transaction) {
        }

        @Override
        public <T> Collection<T> load(final Class<T> cl) {
            return null;
        }

        @Override
        public <T> Collection<T> load(final Class<T> cl, final IXdStorageTransaction transaction) {
            return null;
        }

        @Override
        public void update(final Object object) {
        }

        @Override
        public void update(final Collection<?> objects) {
        }

        @Override
        public void update(final Collection<?> objects, final IXdStorageTransaction transaction) {
        }

        @Override
        public void delete(final Object reference) {
        }

        @Override
        public void delete(final Collection<?> references) {
        }

        @Override
        public void delete(final Collection<?> references, final IXdStorageTransaction transaction) {
        }

        @Override
        public void shutdown() {
        }
    }

    // ------------------------------------------------------------------
    // structure invariants
    // ------------------------------------------------------------------

    private static void checkInvariants(final XdStorageBTree tree) {
        final XdStorageBTreeNode root = tree.getRoot();
        if (root == null) {
            assertNull("first leaf must be null for an empty tree", tree.getFirstLeaf());
            return;
        }

        checkNode(tree, root, null, true);

        final List<XdStorageBTreeNode> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        assertTrue("tree must have leaves", !leaves.isEmpty());
        assertSame("first leaf must be the leftmost leaf", leaves.get(0), tree.getFirstLeaf());

        XdStorageBTreeNode leaf = tree.getFirstLeaf();
        for (final XdStorageBTreeNode expected : leaves) {
            assertSame("leaf chain must follow the in-order leaf sequence", expected, leaf);
            leaf = leaf.getNextTreeNodeOnThisLevel();
        }
        assertNull("leaf chain must end with null", leaf);
    }

    private static void checkNode(final XdStorageBTree tree, final XdStorageBTreeNode node, final XdStorageBTreeNode parent, final boolean isRoot) {
        final int t = tree.getT();

        assertSame("child must point back to its parent", parent, node.getParent());
        assertTrue("node must not hold more than 2t-1 keys (t=" + t + ", found " + node.getKeys().size() + ")",
                node.getKeys().size() <= 2 * t - 1);
        if (!isRoot) {
            assertTrue("non-root node must hold at least t-1 keys (t=" + t + ", found " + node.getKeys().size() + ")",
                    node.getKeys().size() >= t - 1);
        }

        for (int i = 1; i < node.getKeys().size(); ++i) {
            assertTrue("keys must be sorted (" + node.getKeys().get(i - 1) + " > " + node.getKeys().get(i) + ")",
                    node.getKeys().get(i - 1).compareTo(node.getKeys().get(i)) <= 0);
        }

        if (node.getChildren().isEmpty()) {
            assertEquals("leaf must have as many objects as keys", node.getKeys().size(), node.getObjects().size());
            return;
        }

        assertEquals("internal node must have one child more than keys", node.getKeys().size() + 1, node.getChildren().size());
        for (final XdStorageBTreeNode child : node.getChildren()) {
            checkNode(tree, child, node, false);
        }
        for (int i = 0; i < node.getKeys().size(); ++i) {
            assertTrue("separator must not be smaller than the left subtree maximum (index " + i
                            + ", separator=" + node.getKeys().get(i) + ", leftMax=" + maxKey(node.getChildren().get(i)) + ")",
                    node.getKeys().get(i).compareTo(maxKey(node.getChildren().get(i))) >= 0);
            assertEquals("separator must equal the right subtree minimum (index " + i
                            + ", separator=" + node.getKeys().get(i) + ", rightMin=" + minKey(node.getChildren().get(i + 1)) + ")",
                    0, node.getKeys().get(i).compareTo(minKey(node.getChildren().get(i + 1))));
        }
    }

    private static void collectLeaves(final XdStorageBTreeNode node, final List<XdStorageBTreeNode> leaves) {
        if (node.getChildren().isEmpty()) {
            leaves.add(node);
            return;
        }
        for (final XdStorageBTreeNode child : node.getChildren()) {
            collectLeaves(child, leaves);
        }
    }

    private static Comparable minKey(final XdStorageBTreeNode node) {
        XdStorageBTreeNode current = node;
        while (!current.getChildren().isEmpty()) {
            current = current.getChildren().get(0);
        }
        return current.getKeys().get(0);
    }

    private static Comparable maxKey(final XdStorageBTreeNode node) {
        XdStorageBTreeNode current = node;
        while (!current.getChildren().isEmpty()) {
            current = current.getChildren().get(current.getChildren().size() - 1);
        }
        return current.getKeys().get(current.getKeys().size() - 1);
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    public void testInsertAndFindSequential() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        for (int i = 0; i < 100; ++i) {
            tree.insert(i, "obj-" + i, storage, transaction);
            checkInvariants(tree);
        }

        for (int i = 0; i < 100; ++i) {
            final List<Object> found = tree.find(i, storage, transaction);
            assertEquals(1, found.size());
            assertEquals("obj-" + i, found.get(0));
        }
    }

    @Test
    public void testInsertAndFindRandomOrder() throws Exception {
        final List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            keys.add(i);
        }
        Collections.shuffle(keys, new Random(42));

        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 3);

        for (final int key : keys) {
            tree.insert(key, "obj-" + key, storage, transaction);
        }
        checkInvariants(tree);

        for (int i = 0; i < 1000; ++i) {
            assertEquals(1, tree.find(i, storage, transaction).size());
        }
        assertTrue(tree.find(1000, storage, transaction).isEmpty());
        assertTrue(tree.find(-1, storage, transaction).isEmpty());
    }

    @Test
    public void testFindMissingKeyOnEmptyTree() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        assertTrue(tree.find(1, storage, transaction).isEmpty());
    }

    /**
     * Regression: equal keys split across leaf boundaries must all be returned by find.
     * The walk-down has to descend to the left on equality and collect via the leaf chain.
     */
    @Test
    public void testDuplicateKeysAcrossSplits() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        for (int i = 0; i < 30; ++i) {
            tree.insert(7, "obj-" + i, storage, transaction);
        }
        checkInvariants(tree);

        final List<Object> found = tree.find(7, storage, transaction);
        assertEquals("all duplicates must be found across leaf splits", 30, found.size());
    }

    @Test
    public void testMixedDuplicatesFindAll() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final Map<Integer, Integer> model = new TreeMap<>();
        final Random random = new Random(7);
        for (int i = 0; i < 500; ++i) {
            final int key = random.nextInt(40);
            tree.insert(key, "obj-" + i, storage, transaction);
            model.merge(key, 1, Integer::sum);
        }
        checkInvariants(tree);

        for (final Map.Entry<Integer, Integer> entry : model.entrySet()) {
            assertEquals("wrong number of entries for key " + entry.getKey(),
                    (int) entry.getValue(), tree.find(entry.getKey(), storage, transaction).size());
        }
    }

    @Test
    public void testMultipleTreeRejectsDuplicateKey() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), true, 2);

        tree.insert(1, "one", storage, transaction);
        try {
            tree.insert(1, "one-again", storage, transaction);
            fail("duplicate insert into a multiple tree must fail");
        } catch (final XdStorageException expected) {
            // expected
        }
    }

    @Test
    public void testDeleteAllLeavesEmptyTree() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        for (int i = 0; i < 100; ++i) {
            tree.insert(i, "obj-" + i, storage, transaction);
        }
        for (int i = 0; i < 100; ++i) {
            tree.delete(i, storage, transaction);
            checkInvariants(tree);
        }

        assertNull("root must be dropped after the last key is deleted", tree.getRoot());
        for (int i = 0; i < 100; ++i) {
            assertTrue(tree.find(i, storage, transaction).isEmpty());
        }
    }

    @Test
    public void testDeleteMissingKeyThrows() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        tree.insert(1, "one", storage, transaction);
        try {
            tree.delete(2, storage, transaction);
            fail("delete of a missing key must fail");
        } catch (final XdStorageException expected) {
            // expected
        }
        assertEquals(1, tree.find(1, storage, transaction).size());
    }

    @Test
    public void testUpdateReplacesValue() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        for (int i = 0; i < 50; ++i) {
            tree.insert(i, "obj-" + i, storage, transaction);
        }
        tree.update(25, "replaced", storage, transaction);

        assertEquals("replaced", tree.find(25, storage, transaction).get(0));
        checkInvariants(tree);
    }

    @Test
    public void testUpdateMissingKeyThrows() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        tree.insert(1, "one", storage, transaction);
        try {
            tree.update(2, "two", storage, transaction);
            fail("update of a missing key must fail");
        } catch (final XdStorageException expected) {
            // expected
        }
    }

    /**
     * Regression: a root that is an empty leaf (the short window between root creation
     * and the first insert) must produce an empty find result, not a NullPointerException.
     */
    @Test
    public void testFindOnEmptyLeafRootReturnsEmpty() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final XdStorageBTreeNode emptyRoot = new XdStorageBTreeNode(tree, null);
        tree.setRoot(emptyRoot);
        tree.setFirstLeaf(emptyRoot);

        assertTrue(tree.find(1, storage, transaction).isEmpty());
    }

    /**
     * Regression: update on a root that is an empty leaf must throw the regular
     * "does not exist" exception, not an IndexOutOfBoundsException.
     */
    @Test
    public void testUpdateOnEmptyLeafRootThrows() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final XdStorageBTreeNode emptyRoot = new XdStorageBTreeNode(tree, null);
        tree.setRoot(emptyRoot);
        tree.setFirstLeaf(emptyRoot);

        try {
            tree.update(1, "one", storage, transaction);
            fail("update on an empty tree must fail");
        } catch (final XdStorageException expected) {
            // expected
        }
    }

    @Test
    public void testReadReturnsAllEntriesSorted() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final TestTransaction transaction = new TestTransaction();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 300; ++i) {
            keys.add(i);
        }
        Collections.shuffle(keys, new Random(3));
        for (final int key : keys) {
            tree.insert(key, "obj-" + key, storage, transaction);
        }

        final List<Object> seenKeys = new CopyOnWriteArrayList<>();
        tree.read(storage, transaction, (key, value) -> seenKeys.add(key));

        assertEquals(300, seenKeys.size());
        for (int i = 0; i < 300; ++i) {
            assertEquals(i, seenKeys.get(i));
        }
    }

    @Test(timeout = 120000L)
    public void testRandomOperationsAgainstModel() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final Map<Integer, Integer> model = new TreeMap<>();
        final Random random = new Random(20260919);

        for (int op = 0; op < 3000; ++op) {
            final TestTransaction transaction = new TestTransaction();
            final int key = random.nextInt(500);
            final int action = random.nextInt(10);

            if (action < 6 || (action < 7 && model.isEmpty())) {
                tree.insert(key, "obj-" + key, storage, transaction);
                model.merge(key, 1, Integer::sum);
            } else if (action < 8) {
                if (model.getOrDefault(key, 0) > 0) {
                    tree.delete(key, storage, transaction);
                    model.merge(key, -1, Integer::sum);
                }
            } else {
                final List<Object> found = tree.find(key, storage, transaction);
                assertEquals((int) model.getOrDefault(key, 0), found.size());
            }

            if (op % 100 == 0) {
                checkInvariants(tree);
            }
        }

        checkInvariants(tree);
        for (final Map.Entry<Integer, Integer> entry : model.entrySet()) {
            if (entry.getValue() > 0) {
                assertEquals((int) entry.getValue(), tree.find(entry.getKey(), storage, new TestTransaction()).size());
            }
        }
    }

    @Test(timeout = 120000L)
    public void testConcurrentInserts() throws Exception {
        final InMemoryStorage storage = new InMemoryStorage();
        final XdStorageBTree tree = new XdStorageBTree(new XdStorageBTreeId(Object.class, "test"), false, 2);

        final int threads = 4;
        final int keysPerThread = 250;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; ++t) {
            final int base = t * keysPerThread;
            executor.submit(() -> {
                try {
                    start.await();
                    final TestTransaction transaction = new TestTransaction();
                    for (int i = 0; i < keysPerThread; ++i) {
                        tree.insert(base + i, "obj-" + (base + i), storage, transaction);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue("concurrent inserts did not finish in time", executor.awaitTermination(60, TimeUnit.SECONDS));

        checkInvariants(tree);

        final TestTransaction transaction = new TestTransaction();
        for (int i = 0; i < threads * keysPerThread; ++i) {
            assertEquals("obj-" + i, tree.find(i, storage, transaction).get(0));
        }
    }
}
