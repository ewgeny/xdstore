package org.flib.xdstorage.temp;

import org.flib.xdstorage.IXdFileStorage;
import org.flib.xdstorage.XdStorageProvider;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class DebugBTreeDelete {

    public static void main(String[] args) throws Exception {
        final Path folder = new File("./btreedebugstorage").toPath();
        deleteRecursive(folder);
        final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage("btreedebug", "./btreedebugstorage", 250);
        final XdStorageBTreeId treeId = new XdStorageBTreeId(Integer.class, "debug");

        final IXdStorageTransaction tx0 = storage.beginTransaction();
        final XdStorageBTree tree = new XdStorageBTree(treeId, false, 2);
        storage.save(tree, tx0);
        tx0.commit();

        for (int key = 0; key < 120; ++key) {
            final IXdStorageTransaction tx = storage.beginTransaction();
            try {
                tree.insert(key, key * 10, storage, tx);
                tx.commit();
            } catch (final Throwable e) {
                tx.rollback();
                throw e;
            }
        }
        System.out.println("inserted 120 keys");

        for (int key = 0; key < 50; ++key) {
            final IXdStorageTransaction tx = storage.beginTransaction();
            try {
                System.out.println("deleting " + key + " root=" + (tree.getRoot() == null ? "null" : tree.getRoot().getId())
                        + " rootKeys=" + (tree.getRoot() == null ? "-" : tree.getRoot().getKeys().size()));
                tree.delete(key, storage, tx);
                tx.commit();
            } catch (final Throwable e) {
                tx.rollback();
                e.printStackTrace();
                System.out.println("FAILED AT KEY " + key);
                System.exit(1);
            }
        }
        System.out.println("deleted 50 keys ascending");
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
}
