package org.flib.xdstorage.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageIndexResourceCache.
 */
public class XdStorageIndexResourceCacheTest {

    private XdStorageIndexResourceCache cache;

    @BeforeEach
    public void setUp() {
        // Инициализируем кэш с максимальным размером фрагмента = 2 элемента
        cache = new XdStorageIndexResourceCache(2);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testInitialState_ShouldBeClear() {
        assertTrue(cache.isClear());
        assertTrue(cache.getResourcesIds().isEmpty());
    }

    @Test
    public void testInsertAndGetRecord_HappyPath() {
        cache.insertRecord("obj_10", "resource_A");

        assertFalse(cache.isClear());
        assertEquals("resource_A", cache.getResourceId("obj_10"));

        Collection<Object> resources = cache.getResourcesIds();
        assertEquals(1, resources.size());
        assertTrue(resources.contains("resource_A"));
    }

    @Test
    public void testGetFreeResourceId_WithFragmentationLimits() {
        // Кэш пуст — свободных ресурсов нет
        assertNull(cache.getFreeResourceId());

        // Заполняем фрагмент resource_A на 1 элемент (лимит 2)
        cache.insertRecord("obj_1", "resource_A");
        assertEquals("resource_A", cache.getFreeResourceId());

        // Заполняем фрагмент resource_A до лимита (2 элемента)
        cache.insertRecord("obj_2", "resource_A");

        // Теперь resource_A заполнен, свободных ресурсов во фрагменте остаться не должно
        assertNull(cache.getFreeResourceId());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testGetResourceId_WithNonExistentId_ShouldReturnNullSafely() {
        // Граничное условие: Запрос несуществующего идентификатора объекта
        assertNull(cache.getResourceId("ghost_object"));
    }

    @Test
    public void testInsertRecord_WithNullArguments_ShouldStoreSafely() {
        // Граничное условие: Проверяем устойчивость ConcurrentHashMap к null-значениям на входе.
        // Так как это ConcurrentHashMap, вставка null ключа или значения должна выкинуть NPE.
        assertThrows(NullPointerException.class, () -> {
            cache.insertRecord(null, "res_1");
        });
        assertThrows(NullPointerException.class, () -> {
            cache.insertRecord("obj_1", null);
        });
    }
}
