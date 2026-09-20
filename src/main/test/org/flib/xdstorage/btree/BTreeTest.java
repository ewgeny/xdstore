package org.flib.xdstorage.btree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.MockStorage;
import org.flib.xdstorage.XmlDataStorageMultithreadsTest;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.MockTransaction;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BTreeTest {

    private static final Logger                log = LogManager.getLogger(BTreeTest.class);

    private static       IXdStorageTransaction transaction;

    private static IXdStorage storage;

    @BeforeClass
    public static void setUp() {
        storage = new MockStorage();
        transaction = new MockTransaction("transaction-id");
    }

    @Test
    public void singleInsertTest1() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 10; ++i) {
            tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
        }

        for (int i = 0; i < 10; ++i) {
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.size() == 1);
        }
    }

    @Test
    public void singleInsertTest2() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
        }

        for (int i = 0; i < 30; ++i) {
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.size() == 1);
        }
    }

    @Test
    public void multithreadInsertTest1() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(10);
        for (int i = 0; i < 10; ++i) {
            identifiers.add(Integer.valueOf(i));
        }

        identifiers.parallelStream().forEach(i -> {
            try {
                tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });

        for (int i = 0; i < 10; ++i) {
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.size() == 1);
        }
    }

    @Test
    public void multithreadInsertTest2() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(10);
        for (int i = 0; i < 30; ++i) {
            identifiers.add(Integer.valueOf(i));
        }

        identifiers.parallelStream().forEach(i -> {
            try {
                tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });

        for (int i = 0; i < 30; ++i) {
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.size() == 1);
        }
    }

    @Test
    public void singleDeleteTest1() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 10; ++i) {
            tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
        }

        for (int i = 0; i < 10; ++i) {
            tree.delete(Integer.valueOf(i), storage, transaction);
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.isEmpty());
        }
    }

    @Test
    public void singleDeleteTest2() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
        }

        for (int i = 0; i < 30; ++i) {
            tree.delete(Integer.valueOf(i), storage, transaction);
            List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
            Assert.assertTrue(result.isEmpty());
        }
    }

    @Test
    public void singleDeleteTest3() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        for (int i = 0; i < 30; ++i) {
            tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
        }

        for (int i = 0; i < 30; ++i) {
            tree.delete(Integer.valueOf(i), storage, transaction);
            for (int j = i + 1; j < 30; ++j) {
                List<Object> result = tree.find(Integer.valueOf(j), storage, transaction);
                Assert.assertTrue(result.size() == 1);
            }
        }
    }

    @Test
    public void multithreadDeleteTest1() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(10);
        for (int i = 0; i < 10; ++i) {
            identifiers.add(Integer.valueOf(i));
        }

        identifiers.parallelStream().forEach(i -> {
            try {
                tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });

        identifiers.parallelStream().forEach(i -> {
            try {
                List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
                Assert.assertTrue(result.size() == 1);

                tree.delete(Integer.valueOf(i), storage, transaction);

                result = tree.find(Integer.valueOf(i), storage, transaction);
                Assert.assertTrue(result.isEmpty());
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });
    }

    @Test
    public void multithreadDeleteTest2() throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(Object.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, false, 10);

        List<Integer> identifiers = new ArrayList<>(40);
        for (int i = 0; i < 40; ++i) {
            identifiers.add(Integer.valueOf(i));
        }

        identifiers.parallelStream().forEach(i -> {
            try {
                tree.insert(Integer.valueOf(i), new Object(), storage, transaction);
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });

        identifiers.parallelStream().forEach(i -> {
            try {
                List<Object> result = tree.find(Integer.valueOf(i), storage, transaction);
                Assert.assertTrue(result.size() == 1);

                tree.delete(Integer.valueOf(i), storage, transaction);

                result = tree.find(Integer.valueOf(i), storage, transaction);
                Assert.assertTrue(result.isEmpty());
            } catch (Exception e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        });
    }

    @Test
    public void multithreadMixedTest() {
        XdStorageBTreeId btreeId = new XdStorageBTreeId(String.class, "btree");
        XdStorageBTree tree = new XdStorageBTree(btreeId, true, 10);

        final int count = 1000;
        final List<Integer> identifiers = new ArrayList<>(count);
        for (int i = count / 2; i < count; ++i) {
            int key = i + 1;
            identifiers.add(key);
            try {
                tree.insert(key, new Object(), storage, transaction);
            } catch (XdStorageException | XdStorageConnectionException e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        }

        AtomicInteger integer = new AtomicInteger(0);
        identifiers.parallelStream().forEach(i -> {
            buildRandomOperation(storage, transaction, tree, i, integer.incrementAndGet()).run();
        });
    }

    Random rand = new Random();

    private Runnable buildRandomOperation(IXdStorage storage, IXdStorageTransaction transaction, XdStorageBTree tree, Integer i, Integer toInsert) {
        if (rand.nextBoolean()) {
            return new InsertOperation(storage, transaction, tree, toInsert);
        } else {
            return new DeleteOperation(storage, transaction, tree, i);
        }
    }

    private class DeleteOperation implements Runnable {

        IXdStorage storage;

        IXdStorageTransaction transaction;

        XdStorageBTree tree;

        Integer i;

        public DeleteOperation(IXdStorage storage, IXdStorageTransaction transaction, XdStorageBTree tree, Integer i) {
            this.storage = storage;
            this.transaction = transaction;
            this.tree = tree;
            this.i = i;
        }

        @Override
        public void run() {
            try {
                tree.delete(i, storage, transaction);
            } catch (XdStorageException | XdStorageConnectionException e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        }
    }

    private class InsertOperation implements Runnable {

        IXdStorage storage;

        IXdStorageTransaction transaction;

        XdStorageBTree tree;

        Integer i;

        public InsertOperation(IXdStorage storage, IXdStorageTransaction transaction, XdStorageBTree tree, Integer i) {
            this.storage = storage;
            this.transaction = transaction;
            this.tree = tree;
            this.i = i;
        }

        @Override
        public void run() {
            try {
                tree.insert(i, new Object(), storage, transaction);
            } catch (XdStorageException | XdStorageConnectionException e) {
                log.info("error", e);
                Assert.assertNull(e);
            }
        }
    }
}
