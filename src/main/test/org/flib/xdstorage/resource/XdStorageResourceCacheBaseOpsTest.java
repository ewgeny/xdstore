package org.flib.xdstorage.resource;

import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Unit-Группа 1: Базовые операции и наполнение кэша ресурсов")
public class XdStorageResourceCacheBaseOpsTest extends AbstractXdStorageResourceCacheTest {

    @Test
    @DisplayName("fillCache() должен корректно наполнять CHM и увеличивать getObjectsCount()")
    public void testFillCache_ShouldPopulateData() {
        List<Object> nodes = java.util.Arrays.asList(new TestEntity(1L, "V1"), new TestEntity(2L, "V2"));

        resourceCache.fillCache(nodes);

        assertEquals(2, resourceCache.getObjectsCount());
        assertTrue(resourceCache.hasObject(1L));
        assertTrue(resourceCache.hasObject(2L));
        assertFalse(resourceCache.hasObject(3L));
    }

    @Test
    @DisplayName("fillCacheOnlyIfNotExists() не должен перезаписывать уже существующие ключи нод")
    public void testFillCacheOnlyIfNotExists() {
        resourceCache.fillCache(Collections.singletonList(new TestEntity(1L, "Original")));

        resourceCache.fillCacheOnlyIfNotExists(Collections.singletonList(new TestEntity(1L, "Duplicate")));

        assertEquals(1, resourceCache.getObjectsCount());
    }

    @Test
    @DisplayName("fillAndCloneCacheOnlyIfNotExists() должен наполнять кэш и регистрировать транзакционный клон")
    public void testFillAndCloneCacheOnlyIfNotExists() throws Exception {
        XdStorageTransaction tx = createTx("TX-FILL", 1000L);
        List<TestEntity> nodes = Collections.singletonList(new TestEntity(10L, "Bulk"));
        List<TestEntity> resultList = new ArrayList<>();

        resourceCache.fillAndCloneCacheOnlyIfNotExists(nodes, resultList, tx);

        verify(tx, times(1)).startCriticalSection();
        verify(tx, times(1)).finishCriticalSection();
        assertEquals(1, resultList.size());
        assertEquals("Bulk", resultList.get(0).getValue());
        assertTrue(resourceCache.hasObject(10L));
    }
}
