package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Контракт READ COMMITTED для XdStorageResourceCache:
 *  - грязные чтения запрещены (незакоммиченные мутации чужих TX невидимы);
 *  - НЕповторяемое чтение разрешено: после чужого коммита читатель видит
 *    новое закоммиченное значение в СВОЕЙ же открытой транзакции;
 *  - фантомы разрешены: новые закоммиченные строки появляются в последующих сканах;
 *  - закоммиченное удаление исчезает для всех последующих чтений;
 *  - собственные записи видимы всегда.
 */
@DisplayName("READ COMMITTED: контракт изоляции XdStorageResourceCache")
public class XdStorageResourceCacheReadCommittedTest extends AbstractXdStorageResourceCacheTest {

    private XdStorageTransaction writer;
    private XdStorageTransaction reader;

    @BeforeEach
    public void setUpRc() throws Exception {
        resourceCache.setIsolationLevel(XdStorageResourceCache.IsolationLevel.READ_COMMITTED);
        writer = createTx("W", 1000);
        reader = createTx("R", 500); // читатель начал ДО райтера — RC обязан все равно видеть коммиты
        resourceCache.fillCache(java.util.Arrays.asList(
                new TestEntity(1L, "Stable_1"),
                new TestEntity(2L, "Stable_2"),
                new TestEntity(3L, "Stable_3")
        ));
    }

    private List<String> values() throws Exception {
        final List<String> values = new ArrayList<>();
        for (final Object o : resourceCache.read(reader, (IXdStoragePredicate<Object>) e -> true)) {
            final TestEntity e = (TestEntity) org.flib.xdstorage.utils.XdStorageObjectUtils
                    .getWrappedObjectOrSameObject(o);
            values.add(e.getValue());
        }
        return values;
    }

    private void writerCommit() throws Exception {
        final XdStorageTransactionResourceChanges collector =
                new XdStorageTransactionResourceChanges(mock(org.flib.xdstorage.resource.IXdStorageResourceObject.class));
        resourceCache.prepareCommit(writer, collector);
        resourceCache.performCommit(writer, collector);
        resourceCache.commit(writer);
    }

    // =========================================================================
    // Грязные чтения запрещены
    // =========================================================================

    @Test
    @DisplayName("RC-1: незакоммиченная вставка невидима")
    public void noDirtyReadInsert() throws Exception {
        resourceCache.insert(new TestEntity(9L, "Dirty_9"), writer);
        assertFalse(values().contains("Dirty_9"), "Грязная вставка видна!");
    }

    @Test
    @DisplayName("RC-2: незакоммиченный апдейт невидим (читаем старое значение)")
    public void noDirtyReadUpdate() throws Exception {
        resourceCache.update(new TestEntity(1L, "Dirty_1"), writer);
        assertTrue(values().contains("Stable_1"), "Старое значение потеряно");
        assertFalse(values().contains("Dirty_1"), "Грязный апдейт виден!");
    }

    @Test
    @DisplayName("RC-3: незакоммиченное удаление невидимо (строка на месте)")
    public void noDirtyReadDelete() throws Exception {
        resourceCache.delete(2L, writer);
        assertTrue(values().contains("Stable_2"), "Незакоммиченное удаление применилось!");
        assertEquals(3, values().size());
    }

    // =========================================================================
    // Read Committed: неповторяемое чтение и фантомы РАЗРЕШЕНЫ
    // =========================================================================

    @Test
    @DisplayName("RC-4: неповторяемое чтение — апдейт чужого коммита виден в открытой TX")
    public void nonRepeatableReadAllowed() throws Exception {
        assertTrue(values().contains("Stable_1"), "База не заполнена");
        resourceCache.update(new TestEntity(1L, "Committed_1"), writer);
        writerCommit();
        assertTrue(values().contains("Committed_1"),
                "READ COMMITTED нарушен: закоммиченный апдейт невиден открытой TX!");
        assertFalse(values().contains("Stable_1"), "READ COMMITTED: старая версия не заменена");
    }

    @Test
    @DisplayName("RC-5: фантом — чужая закоммиченная вставка видна в открытой TX")
    public void committedInsertVisibleToOpenReaderTx() throws Exception {
        assertEquals(3, values().size(), "База не заполнена");
        resourceCache.insert(new TestEntity(9L, "Committed_9"), writer);
        writerCommit();
        assertTrue(values().contains("Committed_9"), "READ COMMITTED: фантом не появился после коммита!");
        assertEquals(4, values().size());
    }

    @Test
    @DisplayName("RC-6: закоммиченное удаление исчезает для открытой TX")
    public void committedDeleteDisappears() throws Exception {
        assertTrue(values().contains("Stable_2"));
        resourceCache.delete(2L, writer);
        writerCommit();
        assertFalse(values().contains("Stable_2"), "READ COMMITTED: закоммиченное удаление не применилось!");
        assertEquals(2, values().size());
    }

    @Test
    @DisplayName("RC-10: чтение по id — чужой закоммиченный апдейт виден в открытой TX")
    public void flatReadByIdNonRepeatableAllowed() throws Exception {
        final TestEntity before = (TestEntity) resourceCache.read(1L, reader);
        assertEquals("Stable_1", before.getValue(), "Первое чтение по id");

        resourceCache.update(new TestEntity(1L, "Committed_1"), writer);
        writerCommit();

        final TestEntity after = (TestEntity) resourceCache.read(1L, reader);
        assertEquals("Committed_1", after.getValue(),
                "READ COMMITTED: чтение по id не увидело чужой закоммиченный апдейт");
    }

    // =========================================================================
    // Откат и собственные записи в RC-режиме
    // =========================================================================

    @Test
    @DisplayName("RC-7: откат райтера возвращает читателю старое значение")
    public void rollbackRestoresForReader() throws Exception {
        resourceCache.update(new TestEntity(1L, "Dirty_1"), writer);
        resourceCache.rollback(writer);
        assertTrue(values().contains("Stable_1"), "После отката читатель не видит старое значение");
        assertFalse(values().contains("Dirty_1"));
    }

    @Test
    @DisplayName("RC-8: собственные записи читателя-писателя видимы ему (RC не запрещает свои записи)")
    public void ownWritesVisibleInRcTx() throws Exception {
        final XdStorageTransaction own = createTx("OWN", 500);
        resourceCache.insert(new TestEntity(9L, "Mine_9"), own);
        final Object e = resourceCache.read(9L, own);
        assertNotNull(e, "Собственная вставка невидима");
        resourceCache.update(new TestEntity(9L, "Mine_9_v2"), own);
        assertEquals("Mine_9_v2", ((TestEntity) resourceCache.read(9L, own)).getValue());
        resourceCache.delete(9L, own);
        assertNull(resourceCache.read(9L, own), "Собственное удаление не скрыло запись от себя");
    }

    @Test
    @DisplayName("RC-9: identity map держится в RC-режиме")
    public void identityMapInRcMode() throws Exception {
        final Object first = resourceCache.read(1L, reader);
        final Object second = resourceCache.read(1L, reader);
        assertNotNull(first);
        assertSame(first, second, "Identity Map нарушена в RC-режиме");
    }
}
