package org.flib.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.btree.IXdStorageBTreeViewer;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.btree.XdStorageBTreeNode;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drives {@link XdStorageBTree} directly through a real file storage and checks
 * structural invariants of the tree after insert/delete/update phases:
 * node sizes stay inside [t-1, 2t-1] for non root nodes, keys stay sorted,
 * internal nodes keep children == keys + 1, leaves keep keys == objects,
 * parent pointers are consistent and the leaf chain follows the in order
 * traversal. Data level checks (find/read) are compared against a reference model.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BTreeStressTest {

    private static final Logger log = LogManager.getLogger(BTreeStressTest.class);

    private static final String STORAGE_NAME = "btreetest";

    private static final String STORAGE_FOLDER = "./btreeteststorage";

    private static final int T = 2;

    private static IXdFileStorage storage = null;

    private static final XdStorageBTreeId TREE_ID = new XdStorageBTreeId(Integer.class, "stresstest");

    /** reference model: key -> value for live entries (keys are unique except the duplicate smoke test) */
    private final TreeMap<Integer, Integer> model = new TreeMap<>();

    @BeforeClass
    public static void initStorage() throws Exception {
        deleteRecursive(new File(STORAGE_FOLDER).toPath());
        storage = XdStorageProvider.newOrGetFileStorage(STORAGE_NAME, STORAGE_FOLDER, 250);
    }

    @AfterClass
    public static void destroyStorage() {
        storage.shutdown();
    }

    private static void deleteRecursive(final Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            for (final File child : path.toFile().listFiles()) {
                deleteRecursive(child.toPath());
            }
        }
        Files.deleteIfExists(path);
    }

    private XdStorageBTree createTree() throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final XdStorageBTree tree = new XdStorageBTree(TREE_ID, false, T);
            storage.save(tree, tx);
            tx.commit();
            return tree;
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private XdStorageBTree loadTree() throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final XdStorageBTree tree = loadTree(tx);
            tx.commit();
            return tree;
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private XdStorageBTree loadTree(final IXdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        return storage.load(XdStorageBTree.class, TREE_ID, tx);
    }

    private void insert(final XdStorageBTree tree, final int key, final int value) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            tree.insert(key, value, storage, tx);
            tx.commit();
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private void delete(final XdStorageBTree tree, final int key) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            tree.delete(key, storage, tx);
            tx.commit();
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private void update(final XdStorageBTree tree, final int key, final int value) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            tree.update(key, value, storage, tx);
            tx.commit();
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private List<Object> find(final XdStorageBTree tree, final int key) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final List<Object> result = tree.find(key, storage, tx);
            tx.commit();
            return result;
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    /** reads the whole tree through the leaf chain and returns key -> value list */
    private Map<Integer, List<Integer>> readAll(final XdStorageBTree tree) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final Map<Integer, List<Integer>> result = new HashMap<>();
            tree.read(storage, tx, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable key, final Object value) {
                    final Integer intKey = (Integer) key;
                    List<Integer> values = result.get(intKey);
                    if (values == null) {
                        result.put(intKey, values = new ArrayList<>());
                    }
                    values.add((Integer) value);
                }
            });
            tx.commit();
            return result;
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }
    }

    private void loadNodeDeep(final XdStorageBTreeNode node, final IXdStorage storage, final IXdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        if (XdStorageObjectUtils.isReference(node)) {
            storage.load(node, tx);
        }
        for (final XdStorageBTreeNode child : node.getChildren()) {
            loadNodeDeep(child, storage, tx);
        }
    }

    /** walks the tree and asserts all structural invariants, returns the in order list of leaves */
    private List<XdStorageBTreeNode> checkNode(final XdStorageBTreeNode node, final boolean isRoot, final List<XdStorageBTreeNode> leaves) {
        final List<Comparable> keys = node.getKeys();
        final String where = "node " + node.getId();

        assertTrue(where + " has no keys at all", keys.size() > 0);
        if (!isRoot) {
            assertTrue(where + " is underfull: " + keys.size() + " < t-1", keys.size() >= T - 1);
        }
        assertTrue(where + " is overfull: " + keys.size() + " > 2t-1", keys.size() <= 2 * T - 1);

        for (int i = 1; i < keys.size(); ++i) {
            assertTrue(where + " keys are not sorted: " + keys,
                    keys.get(i - 1).compareTo(keys.get(i)) <= 0);
        }

        if (node.getChildren().isEmpty()) {
            assertTrue(where + " is a leaf but objects size differs from keys size",
                    node.getObjects().size() == keys.size());
            leaves.add(node);
        } else {
            assertTrue(where + " is an internal node but has objects", node.getObjects().isEmpty());
            assertTrue(where + " is an internal node but children size " + node.getChildren().size()
                            + " does not match keys size + 1 " + (keys.size() + 1),
                    node.getChildren().size() == keys.size() + 1);
            for (final XdStorageBTreeNode child : node.getChildren()) {
                assertTrue(where + " child has no parent set", child.getParent() != null);
                assertTrue(where + " child parent is not this node", child.getParent() == node);
                checkNode(child, false, leaves);
            }
        }
        return leaves;
    }

    private void checkLeafChain(final XdStorageBTree tree, final List<XdStorageBTreeNode> leaves) {
        XdStorageBTreeNode current = tree.getFirstLeaf();
        int index = 0;
        while (current != null) {
            assertTrue("leaf chain contains more leaves than the tree at index " + index, index < leaves.size());
            assertTrue("leaf chain leaf " + index + " is not the in order leaf of the tree", current == leaves.get(index));
            current = current.getNextTreeNodeOnThisLevel();
            ++index;
        }
        assertEquals("leaf chain length differs from number of leaves in the tree", leaves.size(), index);
    }

    /** full verification of the tree against the reference model and the structural invariants */
    private void verify(final XdStorageBTree tree) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final XdStorageBTree loaded = loadTree(tx);
            assertTrue("tree must be loadable", loaded != null);
            if (loaded.getRoot() != null) {
                loadNodeDeep(loaded.getRoot(), storage, tx);
            }
            tx.commit();

            final List<XdStorageBTreeNode> leaves = new ArrayList<>();
            if (loaded.getRoot() != null) {
                checkNode(loaded.getRoot(), true, leaves);
                checkLeafChain(loaded, leaves);
            } else {
                assertTrue("tree has no root but model is not empty", model.isEmpty());
            }
        } catch (final Throwable e) {
            tx.rollback();
            throw e;
        }

        // data level checks against the model
        for (final Map.Entry<Integer, Integer> entry : model.entrySet()) {
            final List<Object> found = find(tree, entry.getKey());
            assertTrue("key " + entry.getKey() + " must be found", !found.isEmpty());
            boolean valueFound = false;
            for (final Object value : found) {
                if (value.equals(entry.getValue())) {
                    valueFound = true;
                }
            }
            assertTrue("key " + entry.getKey() + " must return value " + entry.getValue() + " but returned " + found, valueFound);
        }

        int absentChecks = 0;
        for (int key = -2; key < 1200 && absentChecks < 30; ++key) {
            if (!model.containsKey(key)) {
                assertTrue("key " + key + " is absent in the model but was found in the tree", find(tree, key).isEmpty());
                ++absentChecks;
            }
        }
        assertTrue("not enough absent keys checked", absentChecks >= 10);

        final Map<Integer, List<Integer>> all = readAll(tree);
        assertEquals("read size differs from model size", model.size(), all.size());
        for (final Map.Entry<Integer, Integer> entry : model.entrySet()) {
            assertTrue("read must contain key " + entry.getKey(), all.containsKey(entry.getKey()));
            assertTrue("read must return value " + entry.getValue() + " for key " + entry.getKey() + " but returned " + all.get(entry.getKey()),
                    all.get(entry.getKey()).contains(entry.getValue()));
        }
    }

    @Test
    public void test1InsertSequential() throws Exception {
        final XdStorageBTree tree = createTree();

        for (int key = 0; key < 120; ++key) {
            insert(tree, key, key * 10);
            model.put(key, key * 10);
        }
        verify(tree);
        log.info("test1InsertSequential passed");
    }

    @Test
    public void test2DeleteAscending() throws Exception {
        final XdStorageBTree tree = loadTree();

        for (int key = 0; key < 50; ++key) {
            delete(tree, key);
            model.remove(key);
        }
        verify(tree);
        log.info("test2DeleteAscending passed");
    }

    /**
     * deletes from the right end of the key range: the rightmost leaf underflows
     * and has to borrow from or join with the left neighbor
     */
    @Test
    public void test3DeleteDescending() throws Exception {
        final XdStorageBTree tree = loadTree();

        // make siblings on the right side fat again before cutting them down
        for (int key = 120; key < 170; ++key) {
            insert(tree, key, key * 10);
            model.put(key, key * 10);
        }
        verify(tree);

        for (int key = 169; key >= 60; --key) {
            delete(tree, key);
            model.remove(key);
        }
        verify(tree);
        log.info("test3DeleteDescending passed");
    }

    @Test
    public void test4UpdateValues() throws Exception {
        final XdStorageBTree tree = loadTree();

        for (int key = 50; key < 60; ++key) {
            update(tree, key, key * 100);
            model.put(key, key * 100);
        }
        verify(tree);
        log.info("test4UpdateValues passed");
    }

    @Test
    public void test5RandomInterleaved() throws Exception {
        final XdStorageBTree tree = loadTree();

        final Random random = new Random(42);
        final List<Integer> pool = new ArrayList<>();
        for (int key = 200; key < 320; ++key) {
            pool.add(key);
        }

        for (int step = 0; step < 500; ++step) {
            final boolean doInsert = random.nextInt(10) < 6 || model.isEmpty();
            if (doInsert && !pool.isEmpty()) {
                final int key = pool.remove(random.nextInt(pool.size()));
                insert(tree, key, key * 10);
                model.put(key, key * 10);
            } else {
                final List<Integer> live = new ArrayList<>(model.keySet());
                final int key = live.get(random.nextInt(live.size()));
                delete(tree, key);
                model.remove(key);
            }
            if (step % 50 == 49) {
                verify(tree);
            }
        }
        verify(tree);
        log.info("test5RandomInterleaved passed");
    }

    @Test
    public void test6DuplicateKeysAndEmptyTree() throws Exception {
        final XdStorageBTree tree = loadTree();

        // multiple == false allows several objects under one key
        final int key = 55;
        final Integer firstValue = model.get(key);
        insert(tree, key, 999999);
        final List<Object> found = find(tree, key);
        assertTrue("duplicate key must return two objects but returned " + found.size(), found.size() == 2);
        assertTrue(found.contains(firstValue) && found.contains(999999));

        // delete removes all objects of the key
        delete(tree, key);
        model.remove(key);
        assertTrue("key must be gone", find(tree, key).isEmpty());
        verify(tree);

        // delete everything and check the tree resets itself to the empty state
        final List<Integer> live = new ArrayList<>(model.keySet());
        for (final int liveKey : live) {
            delete(tree, liveKey);
            model.remove(liveKey);
        }
        verify(tree);

        final XdStorageBTree empty = loadTree();
        assertTrue("root must be null after deleting everything", empty.getRoot() == null);
        assertTrue("find on empty tree must be empty", find(empty, 1).isEmpty());

        // insert after full cleanup works again
        insert(tree, 1, 10);
        model.put(1, 10);
        verify(tree);
        log.info("test6DuplicateKeysAndEmptyTree passed");
    }
}
