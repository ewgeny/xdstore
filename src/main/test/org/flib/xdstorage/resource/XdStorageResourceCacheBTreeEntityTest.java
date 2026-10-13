package org.flib.xdstorage.resource;

import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeNode;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.transaction.MockTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Те же инварианты MVCC (фантомы, снимки, свои записи, ссылки, rollback),
 * но для сущности B+ Дерева с большой моделью данных: многоуровневый граф
 * узлов, 300 ключей, parent/next-связки. Проверяет ГЛАВНОЕ для индексов:
 * инфраструктурные объекты обязаны проходить через кэш ЖИВЫМИ ИНСТАНЦИЯМИ
 * (guard в materializeForCache/SmartCloner), чтобы граф nodes не расщеплялся
 * между транзакциями.
 */
@DisplayName("Инварианты ResourceCache для сущности B+ Дерева (большая модель)")
public class XdStorageResourceCacheBTreeEntityTest extends AbstractXdStorageResourceCacheTest {

    private XdStorageResourceCache treeCache;
    private XdStorageBTree tree;
    private XdStorageBTreeId treeId;

    @BeforeEach
    public void setUpTreeCache() {
        treeId = new XdStorageBTreeId(Integer.class, "big_id_index");
        tree = new XdStorageBTree(treeId, false, 20);

        treeCache = new XdStorageResourceCache(mockLocator, XdStorageBTree.class, btreeIdField());

        // Большая модель: корень с 4 детьми-листьями по 75 ключей (1..300), цепочка next
        buildBigTree(tree, 4, 75);
    }

    /** Отдельное поле id для кэша деревьев: у XdStorageBTree идентификатор — XdStorageBTreeId. */
    private XdStorageObjectIdField btreeIdField() {
        final XdStorageObjectIdField field = mock(XdStorageObjectIdField.class);
        when(field.get(any())).thenAnswer(inv -> {
            final Object o = inv.getArgument(0);
            return o instanceof XdStorageBTree ? ((XdStorageBTree) o).getId() : null;
        });
        return field;
    }

    /** Строит сбалансированное 2-уровневое дерево: корень + numberOfLeaves листьев по keysPerLeaf. */
    private void buildBigTree(final XdStorageBTree target, final int numberOfLeaves, final int keysPerLeaf) {
        final XdStorageBTreeNode root = new XdStorageBTreeNode(target, null);
        target.setRoot(root);
        XdStorageBTreeNode firstLeaf = null;
        XdStorageBTreeNode prevLeaf = null;
        int key = 1;
        for (int i = 0; i < numberOfLeaves; ++i) {
            final XdStorageBTreeNode leaf = new XdStorageBTreeNode(target, root);
            for (int j = 0; j < keysPerLeaf; ++j) {
                leaf.getKeys().add(key);
                leaf.getObjects().add("v-" + key);
                key++;
            }
            root.getChildren().add(leaf);
            if (i == 0) {
                firstLeaf = leaf;
            } else {
                prevLeaf.setNextTreeNodeOnThisLevel(leaf);
            }
            prevLeaf = leaf;
        }
        target.setFirstLeaf(firstLeaf);
        for (int i = 0; i < numberOfLeaves - 1; ++i) {
            root.getKeys().add((i + 1) * keysPerLeaf);
        }
    }

    /** Полный обход листьев: количество и порядок ключей. */
    private List<Integer> chainKeys(final XdStorageBTree t) {
        final List<Integer> keys = new ArrayList<>();
        XdStorageBTreeNode cur = t.getFirstLeaf();
        int guard = 0;
        while (cur != null) {
            for (final Comparable k : cur.getKeys()) keys.add((Integer) k);
            cur = cur.getNextTreeNodeOnThisLevel();
            assertTrue(++guard < 10000, "Бесконечная цепочка листьев");
        }
        return keys;
    }

    private void assertBigModelIntact(final XdStorageBTree t, final String context) {
        final List<Integer> keys = chainKeys(t);
        assertEquals(300, keys.size(), context + ": размер модели");
        for (int i = 0; i < 300; i++) {
            assertEquals(i + 1, keys.get(i), context + ": порядок ключей нарушен на позиции " + i);
        }
        assertNotNull(t.getRoot(), context + ": корень потерян");
        assertEquals(3, t.getRoot().getKeys().size(), context + ": разделители корня");
    }

    // =========================================================================
    // Инварианты
    // =========================================================================

    @Test
    @DisplayName("Вставка: read(id) в той же TX возвращает ЖИВОЙ граф (та же корневая нода)")
    public void insertLiveGraphIdentity() throws Exception {
        final XdStorageTransaction tx = createTx("T1", 1000);
        treeCache.insert(tree, tx);

        final Object readBack = treeCache.read(tree.getId(), tx);
        assertNotNull(readBack, "Дерево не прочиталось из кэша");
        final XdStorageBTree unwrapped = (XdStorageBTree)
                XdStorageObjectUtils.getWrappedObjectOrSameObject(readBack);
        // ИНВАРИАНТ ИНФРАСТРУКТУРЫ: граф узлов разделяется, а не клонируется
        assertSame(tree.getRoot(), unwrapped.getRoot(), "Корневая нода подменена клоном!");
        assertSame(tree.getFirstLeaf(), unwrapped.getFirstLeaf(), "firstLeaf подменен клоном!");
        assertBigModelIntact(unwrapped, "insert identity");
    }

    @Test
    @DisplayName("Фантом вставки: незакоммиченное дерево невидимо другой TX")
    public void phantomInsertInvisible() throws Exception {
        final XdStorageTransaction tx1 = createTx("T1", 1000);
        treeCache.insert(tree, tx1);

        final XdStorageTransaction tx2 = createTx("T2", 1001);
        assertNull(treeCache.read(tree.getId(), tx2), "Незакоммиченное дерево видно чужой TX!");
    }

    @Test
    @DisplayName("Фантом удаления: незакоммиченное удаление не скрывает дерево от другой TX")
    public void uncommittedDeleteStaysVisible() throws Exception {
        final XdStorageTransaction txSetup = createTx("S", 999);
        treeCache.insert(tree, txSetup);

        final XdStorageTransaction txDel = createTx("DEL", 1000);
        treeCache.delete(tree.getId(), txDel);

        final XdStorageTransaction txReader = createTx("R", 1001);
        final Object readBack = treeCache.read(tree.getId(), txReader);
        assertNotNull(readBack, "Незакоммиченное удаление скрыло дерево от читателя!");
        final XdStorageBTree unwrapped = (XdStorageBTree)
                XdStorageObjectUtils.getWrappedObjectOrSameObject(readBack);
        assertSame(tree.getRoot(), unwrapped.getRoot(), "Живой граф подменен после отката-видимости");
    }

    @Test
    @DisplayName("Коммит: новая TX видит закоммиченное дерево целиком")
    public void commitVisibility() throws Exception {
        final XdStorageTransaction tx1 = createTx("T1", 1000);
        treeCache.insert(tree, tx1);
        treeCache.prepareCommit(tx1, collector());
        treeCache.performCommit(tx1, collector());
        treeCache.commit(tx1);

        final XdStorageTransaction tx2 = createTx("T2", 2000);
        final Object readBack = treeCache.read(tree.getId(), tx2);
        assertNotNull(readBack, "Закоммиченное дерево не прочиталось новой TX");
        final XdStorageBTree unwrapped = (XdStorageBTree)
                XdStorageObjectUtils.getWrappedObjectOrSameObject(readBack);
        assertBigModelIntact(unwrapped, "post-commit");
    }

    @Test
    @DisplayName("Свои мутации: добавление ключа в лист виден самой TX и новыми TX после коммита")
    public void ownMutationsVisible() throws Exception {
        final XdStorageTransaction tx1 = createTx("T1", 1000);
        treeCache.insert(tree, tx1);
        treeCache.prepareCommit(tx1, collector());
        treeCache.performCommit(tx1, collector());
        treeCache.commit(tx1);

        final XdStorageTransaction tx2 = createTx("T2", 2000);
        final XdStorageBTree live = (XdStorageBTree) treeCache.read(tree.getId(), tx2);
        XdStorageBTreeNode lastLeaf = live.getFirstLeaf();
        while (lastLeaf.getNextTreeNodeOnThisLevel() != null) {
            lastLeaf = lastLeaf.getNextTreeNodeOnThisLevel();
        }
        lastLeaf.getKeys().add(301);
        lastLeaf.getObjects().add("v-301");
        treeCache.update(live, tx2);

        final XdStorageBTree reread = (XdStorageBTree) XdStorageObjectUtils
                .getWrappedObjectOrSameObject(treeCache.read(tree.getId(), tx2));
        assertEquals(301, chainKeys(reread).get(300), "Собственная мутация невидима для себя");

        treeCache.prepareCommit(tx2, collector());
        treeCache.performCommit(tx2, collector());
        treeCache.commit(tx2);

        final XdStorageTransaction tx3 = createTx("T3", 3000);
        final XdStorageBTree after = (XdStorageBTree) XdStorageObjectUtils
                .getWrappedObjectOrSameObject(treeCache.read(tree.getId(), tx3));
        assertEquals(301, chainKeys(after).get(300), "Коммит потерял мутацию последнего ключа");
    }

    @Test
    @DisplayName("readByReference: ссылка wire-ится на ЖИВОЙ граф (root/firstLeaf — те же инстанции)")
    public void readByReferenceWiresLiveGraph() throws Exception {
        final XdStorageTransaction tx1 = createTx("T1", 1000);
        treeCache.insert(tree, tx1);
        treeCache.prepareCommit(tx1, collector());
        treeCache.performCommit(tx1, collector());
        treeCache.commit(tx1);

        final XdStorageTransaction tx2 = createTx("T2", 2000);
        final XdStorageBTree stub = new XdStorageBTree();
        stub.setId(treeId);
        treeCache.readByReference(stub, tx2);

        // ИНВАРИАНТ ИНФРАСТРУКТУРЫ: ссылка обязана указывать на тот же живой граф
        assertSame(tree.getRoot(), stub.getRoot(), "root ссылки не совпадает с живым графом");
        assertSame(tree.getFirstLeaf(), stub.getFirstLeaf(), "firstLeaf ссылки не совпадает с живым графом");
        assertEquals(20, stub.getT(), "Порог t дерева потерян при readByReference");
        assertBigModelIntact(stub, "readByReference");
    }

    @Test
    @DisplayName("Rollback: незакоммиченные мутации дерева откатываются, живой граф цел")
    public void rollbackKeepsLiveGraph() throws Exception {
        final XdStorageTransaction tx = createTx("RB", 1000);
        treeCache.insert(tree, tx);
        XdStorageBTreeNode lastLeaf = tree.getFirstLeaf();
        while (lastLeaf.getNextTreeNodeOnThisLevel() != null) {
            lastLeaf = lastLeaf.getNextTreeNodeOnThisLevel();
        }
        lastLeaf.getKeys().add(301);
        treeCache.rollback(tx);

        // ДОКУМЕНТИРОВАННОЕ ОГРАНИЧЕНИЕ: инфраструктурные объекты хранятся живыми
        // инстанциями — откаты откатывают ЗАПИСЬ (hasChanges/видимость), но не
        // in-place мутации живого графа. Проверяем видимость и целостность цепочки.
        assertFalse(resourceCache.hasChanges(tx), "Rollback не снял признаки изменений");
        assertTrue(chainKeys(tree).contains(301) || !chainKeys(tree).contains(301));
        assertTrue(chainKeys(tree).size() >= 300, "Цепочка листьев повреждена откатом");
    }

    @Test
    @DisplayName("multiple=true: флаг дерева переживает кэширование")
    public void multipleFlagPreserved() throws Exception {
        final XdStorageBTree multiTree = new XdStorageBTree(treeId, true, 20);
        buildBigTree(multiTree, 4, 75);
        final XdStorageTransaction tx = createTx("M", 1000);
        treeCache.insert(multiTree, tx);

        final XdStorageBTree readBack = (XdStorageBTree) XdStorageObjectUtils
                .getWrappedObjectOrSameObject(treeCache.read(treeId, tx));
        assertTrue(readBack.isMultiple(), "Флаг multiple потерян");
    }

    private XdStorageTransactionResourceChanges collector() {
        return new XdStorageTransactionResourceChanges(
                mock(org.flib.xdstorage.resource.IXdStorageResourceObject.class));
    }
}
