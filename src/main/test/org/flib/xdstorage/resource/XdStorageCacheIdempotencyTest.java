package org.flib.xdstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("3. Идемпотентность и граничные барьеры ошибок СУБД")
public class XdStorageCacheIdempotencyTest extends AbstractCacheResourceTest {

    @Test
    @DisplayName("Метод fillCacheOnlyIfNotExists должен гасить дубликаты")
    public void testFillCacheOnlyIfNotExists_ShouldIgnoreDuplicates() {
        TestEntity entity = new TestEntity("Original");
        long entityId = (long) entity.hashCode();

        resourceCache.fillCache(Collections.singletonList(entity));
        assertEquals(1, resourceCache.getObjectsCount());

        TestEntity duplicateEntity = new TestEntity("Duplicate");
        when(mockIdField.get(duplicateEntity)).thenReturn(entityId);

        resourceCache.fillCacheOnlyIfNotExists(Collections.singletonList(duplicateEntity));
        assertEquals(1, resourceCache.getObjectsCount());
    }

    @Test
    @DisplayName("Повторный каскадный Insert одного ID должен гаситься транзакционным барьером")
    public void testInsert_IdempotentBarrier_ShouldNotThrowException() throws Exception {
        XdStorageTransaction tx = mockTransaction("tx-single");
        TestEntity entity = new TestEntity("Target");
        long id = 777L;
        when(mockIdField.get(entity)).thenReturn(id);
        when(mockCloner.unwrapAndClone(entity)).thenReturn(entity);

        assertDoesNotThrow(() -> resourceCache.insert(entity, tx));
        assertDoesNotThrow(() -> resourceCache.insert(entity, tx), "Повторный insert должен гаситься идемпотентно!");
    }

    @Test
    @DisplayName("Удаление (Delete) несуществующего объекта должно бросать XdStorageException")
    public void testDelete_MissingObject_ShouldThrowXdStorageException() {
        XdStorageTransaction tx = mockTransaction("tx-del");
        long phantomId = 99999L;

        XdStorageException thrown = assertThrows(XdStorageException.class, () -> {
            resourceCache.delete(phantomId, tx);
        });
        assertTrue(thrown.getMessage().contains("does not exists"));
    }

    @Test
    @DisplayName("Обновление (Update) несуществующего объекта должно падать с ошибкой")
    public void testUpdate_MissingObject_ShouldThrowXdStorageException() {
        XdStorageTransaction tx = mockTransaction("tx-upd");
        TestEntity phantom = new TestEntity("Phantom");
        when(mockIdField.get(phantom)).thenReturn(555L);

        assertThrows(XdStorageException.class, () -> {
            resourceCache.update(phantom, tx);
        });
    }
}
