package org.flib.xdstorage.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStoragePair.
 */
public class XdStoragePairTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testPairCreationAndGetters_WithValidObjects() {
        String key = "testKey";
        Integer value = 42;

        XdStoragePair<String, Integer> pair = new XdStoragePair<>(key, value);

        // Проверяем корректность сохранения и возврата данных через геттеры
        // Если геттеры называются по-другому (например, getKey()/getValue() или getFirst()/getSecond()),
        // компилятор сразу подсветит это, и мы адаптируем вызовы под ваш точный контракт.
        assertEquals(key, pair.getKey());
        assertEquals(value, pair.getValue());
    }

    @Test
    public void testEqualsAndHashCode_WithIdenticalPairs_ShouldBeEqual() {
        XdStoragePair<String, String> pair1 = new XdStoragePair<>("A", "B");
        XdStoragePair<String, String> pair2 = new XdStoragePair<>("A", "B");

        assertEquals(pair1, pair2);
        assertEquals(pair1.hashCode(), pair2.hashCode());
    }

    @Test
    public void testEquals_WithDifferentPairs_ShouldNotBeEqual() {
        XdStoragePair<String, String> pair1 = new XdStoragePair<>("A", "B");
        XdStoragePair<String, String> pair2 = new XdStoragePair<>("A", "C");
        XdStoragePair<String, String> pair3 = new XdStoragePair<>("X", "B");

        assertNotEquals(pair1, pair2);
        assertNotEquals(pair1, pair3);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testPair_WithNullKeyOrNullValue_ShouldAllowNulls() {
        // Граничное условие: один из элементов или оба элемента равны null.
        // Проверяем, что конструктор не выбрасывает NullPointerException.
        XdStoragePair<String, String> pairWithNullKey = new XdStoragePair<>(null, "value");
        XdStoragePair<String, String> pairWithNullValue = new XdStoragePair<>("key", null);
        XdStoragePair<String, String> pairAllNulls = new XdStoragePair<>(null, null);

        assertNull(pairWithNullKey.getKey());
        assertEquals("value", pairWithNullKey.getValue());

        assertEquals("key", pairWithNullValue.getKey());
        assertNull(pairWithNullValue.getValue());

        assertNull(pairAllNulls.getKey());
        assertNull(pairAllNulls.getValue());
    }

    @Test
    public void testEqualsAndHashCode_WithNullElements_ShouldHandleSafely() {
        // Граничное условие: сравнение пар, содержащих null.
        // Это частая точка падения СУБД, если внутри equals/hashCode нет проверки на null.
        XdStoragePair<String, String> pair1 = new XdStoragePair<>(null, "B");
        XdStoragePair<String, String> pair2 = new XdStoragePair<>(null, "B");
        XdStoragePair<String, String> pair3 = new XdStoragePair<>("A", null);
        XdStoragePair<String, String> pair4 = new XdStoragePair<>("A", null);

        assertEquals(pair1, pair2);
        assertEquals(pair1.hashCode(), pair2.hashCode());

        assertEquals(pair3, pair4);
        assertEquals(pair3.hashCode(), pair4.hashCode());

        assertNotEquals(pair1, pair3);
    }

    @Test
    public void testEquals_WithDifferentObjectTypes_ShouldReturnFalseSafely() {
        // Граничное условие: сравнение пары с объектом другого класса или null.
        XdStoragePair<String, String> pair = new XdStoragePair<>("A", "B");

        assertNotEquals(null, pair);
        assertNotEquals("just a string", pair);
    }
}
