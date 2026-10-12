package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Контроль структурных инвариантов B+ Дерева индексов на границе транзакционных операций.
 * Прогоняется после мутаций индекса (insert/delete) в контуре транзакции.
 *
 * Проверяет три инварианта:
 * 1. Целостность вершин: каждый child каждого внутреннего узла разрешается в живую ноду
 *    (ловит висячие ссылки после слияний — merge удалил ноду, а родитель ее все еще держит).
 * 2. Паритет keys/objects в каждом листе (ловит разъехавшиеся списки после split).
 * 3. Эквивалентность множества листьев, достижимых из корня, и множества листьев,
 *    достижимых из firstLeaf по цепочке next (ловит обрыв цепочки: лист существует
 *    в дереве, но диапазонные сканы его не видят — дефект 268/1250).
 * 4. Глобальную неубываемость ключей вдоль цепочки листьев (ловит неверную сшивку split/merge).
 *
 * При любом нарушении выбрасывается XdStorageException — транзакция обязана откатиться громко,
 * а не фиксировать битую структуру на диск молча.
 */
public final class XdStorageBTreeInvariants {

    private XdStorageBTreeInvariants() {
        // utility class
    }

    /**
     * Полная структурная валидация дерева индекса после мутации.
     * Выполняется под write-локом дерева: мутаторы держат этот лок на всю операцию,
     * поэтому валидатор не может наблюдать промежуточные состояния ребалансировки.
     */
    public static void validateLeafChain(final XdStorageBTree tree, final IXdStorage storage,
                                         final XdStorageTransaction transaction, final String context)
            throws XdStorageException, XdStorageConnectionException {
        if (tree == null) {
            return;
        }

        tree.lockWrite(transaction);
        try {
            validateUnderTreeLock(tree, storage, transaction, context);
        } finally {
            tree.unlockWrite();
        }
    }

    private static void validateUnderTreeLock(final XdStorageBTree tree, final IXdStorage storage,
                                              final XdStorageTransaction transaction, final String context)
            throws XdStorageException, XdStorageConnectionException {
        if (tree == null) {
            return;
        }

        // ИНВАРИАНТ 1 + сбор множества листьев, достижимых из корня
        final Set<Object> leavesViaRoot = new HashSet<>();
        if (tree.getRoot() != null) {
            descendCollectingLeaves(tree.getRoot(), storage, transaction, leavesViaRoot, context, new HashSet<>());
        }

        if (tree.getFirstLeaf() == null) {
            if (!leavesViaRoot.isEmpty()) {
                throw new XdStorageException(context + ": firstLeaf пуст, но из корня достижимо листьев: " + leavesViaRoot.size());
            }
            return;
        }

        // ИНВАРИАНТ 2 + 4 + сбор множества листьев цепочки
        final Set<Object> leavesViaChain = new HashSet<>();
        final XdStorageException[] failure = new XdStorageException[]{null};
        final Comparable[] previousKey = new Comparable[]{null};

        XdStorageBTreeNode current = tree.getFirstLeaf();
        int guard = 0;
        while (current != null) {
            if (failure[0] != null) {
                throw failure[0];
            }
            if (current.getNextTreeNodeOnThisLevel() == current) {
                throw new XdStorageException(context + ": лист " + current.getId() + " замкнут сам на себя");
            }
            if (!leavesViaChain.add(current.getId())) {
                throw new XdStorageException(context + ": цикл в цепочке листьев — лист " + current.getId() + " встречается повторно");
            }
            if (current.getKeys().size() != current.getObjects().size()) {
                throw new XdStorageException(context + ": лист " + current.getId() + " имеет keys=" + current.getKeys().size()
                        + " при objects=" + current.getObjects().size() + " — списки разошлись");
            }
            for (final Comparable key : current.getKeys()) {
                if (key == null) {
                    throw new XdStorageException(context + ": лист " + current.getId() + " содержит null-ключ");
                }
                if (previousKey[0] != null && previousKey[0].compareTo(key) > 0) {
                    throw new XdStorageException(context + ": нарушение сортировки в цепочке листьев: " + previousKey[0] + " > " + key
                            + " (лист " + current.getId() + ") — некорректный split/merge");
                }
                previousKey[0] = key;
            }

            final XdStorageBTreeNode next = current.getNextTreeNodeOnThisLevel();
            if (next != null && XdStorageObjectUtils.isReference(next)) {
                storage.load(next, transaction);
            }
            current = next;

            if (++guard > 100000) {
                throw new XdStorageException(context + ": цепочка листьев длиннее 100000 — защита от зацикливания");
            }
        }

        // ИНВАРИАНТ 3: множество листьев из корня == множество листьев цепочки
        if (!leavesViaRoot.equals(leavesViaChain)) {
            final Set<Object> onlyViaRoot = new HashSet<>(leavesViaRoot);
            onlyViaRoot.removeAll(leavesViaChain);
            final Set<Object> onlyViaChain = new HashSet<>(leavesViaChain);
            onlyViaChain.removeAll(leavesViaRoot);
            throw new XdStorageException(context + ": рассинхрон структуры: листьев только из корня: " + onlyViaRoot
                    + ", листьев только из цепочки: " + onlyViaChain
                    + " (из корня: " + leavesViaRoot.size() + ", из цепочки: " + leavesViaChain.size() + ")");
        }
    }

    private static void descendCollectingLeaves(final XdStorageBTreeNode node, final IXdStorage storage,
                                                final XdStorageTransaction transaction, final Set<Object> leaves,
                                                final String context, final Set<Object> visited)
            throws XdStorageException, XdStorageConnectionException {
        if (node == null) {
            return;
        }
        if (!visited.add(node.getId())) {
            throw new XdStorageException(context + ": цикл/дубль в descend-обходе — нода " + node.getId() + " встречена повторно");
        }

        if (node.getChildren().isEmpty()) {
            leaves.add(node.getId());
            return;
        }

        final List<XdStorageBTreeNode> children = new ArrayList<>(node.getChildren());
        for (final XdStorageBTreeNode child : children) {
            if (child == null) {
                throw new XdStorageException(context + ": внутренняя нода " + node.getId() + " содержит null-ребенка");
            }
            XdStorageBTreeNode resolved = child;
            if (XdStorageObjectUtils.isReference(child)) {
                try {
                    storage.load(child, transaction);
                } catch (final Throwable t) {
                    throw new XdStorageException(context + ": ВИСЯЧАЯ ССЫЛКА — внутренняя нода " + node.getId()
                            + " ссылается на ребенка " + child.getId() + ", который не разрешается: " + t.getMessage(), t);
                }
                resolved = child;
            }
            descendCollectingLeaves(resolved, storage, transaction, leaves, context, visited);
        }
    }
}
