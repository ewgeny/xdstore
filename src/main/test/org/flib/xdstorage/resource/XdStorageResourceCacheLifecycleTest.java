package org.flib.xdstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Unit-Группа 4: Транзакционный жизненный цикл и Двухфазный коммит (2PC)")
public class XdStorageResourceCacheLifecycleTest extends AbstractXdStorageResourceCacheTest {

    private XdStorageTransaction tx;
    private XdStorageTransactionResourceChanges collector;

    @BeforeEach
    public void initTxContext() throws Exception {
        tx = createTx("TX-ACID", 1000L);
        collector = new XdStorageTransactionResourceChanges(mock(org.flib.xdstorage.resource.XdStorageResource.class));
        resourceCache.fillCache(Collections.singletonList(new TestEntity(777L, "Base")));

        TestEntity node = new TestEntity(777L, "Mutated");
        resourceCache.update(node, tx);
    }

    @Test
    @DisplayName("Прямой commit() без прохождения фаз подготовки 2PC обязан падать с исключением")
    public void testCommit_Without2PhasePreparation_ShouldThrowException() {
        assertThrows(XdStorageRuntimeException.class, () -> resourceCache.commit(tx),
                "Инвариант двухфазной фиксации пробит! Кэш позволил сделать commit() без prepare/perform!");
    }

    @Test
    @DisplayName("Каноническая цепочка 2PC (prepare -> perform -> commit) должна успешно фиксировать изменения")
    public void testCommit_Full2PCSymmetricSuccess() throws Exception {
        resourceCache.prepareCommit(tx, collector);
        resourceCache.performCommit(tx, collector);

        assertDoesNotThrow(() -> resourceCache.commit(tx));

        XdStorageTransaction freshReaderTx = createTx("TX-READER", 2000L);
        TestEntity committedNode = (TestEntity) resourceCache.read(777L, freshReaderTx);
        assertEquals("Mutated", committedNode.getValue());
    }

    @Test
    @DisplayName("rollback() должен полностью аннулировать транзитные изменения сессии")
    public void testRollback_ShouldRevertChanges() throws Exception {
        resourceCache.rollback(tx);

        XdStorageTransaction freshReaderTx = createTx("TX-READER", 2000L);
        TestEntity revertedNode = (TestEntity) resourceCache.read(777L, freshReaderTx);
        assertEquals("Base", revertedNode.getValue());
    }

    @Test
    @DisplayName("clear() должен бесшовно вырезать транзакционные контексты из оперативной памяти")
    public void testClear_ShouldPurgeTransactionContextMaps() throws Exception {
        assertTrue(resourceCache.hasChanges(tx));

        resourceCache.clear(tx);

        assertFalse(resourceCache.hasChanges(tx));
    }
}
