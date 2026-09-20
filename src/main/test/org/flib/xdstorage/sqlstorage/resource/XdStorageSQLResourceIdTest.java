package org.flib.xdstorage.sqlstorage.resource;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageSQLResourceId.
 */
public class XdStorageSQLResourceIdTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();

        resourceId.setTable("users_table");
        resourceId.setDataSource("postgresql_shard_1");

        assertEquals("users_table", resourceId.getTable());
        assertEquals("postgresql_shard_1", resourceId.getDataSource());
    }

    @Test
    public void testEqualsAndHashCode_WithIdenticalFields_ShouldBeEqual() {
        XdStorageSQLResourceId id1 = new XdStorageSQLResourceId();
        id1.setTable("orders");
        id1.setDataSource("shard_central");

        XdStorageSQLResourceId id2 = new XdStorageSQLResourceId();
        id2.setTable("orders");
        id2.setDataSource("shard_central");

        // Проверяем каноничную эквивалентность и контракты хэш-кодов
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    public void testEquals_WithDifferentFields_ShouldNotBeEqual() {
        XdStorageSQLResourceId id1 = new XdStorageSQLResourceId();
        id1.setTable("orders");
        id1.setDataSource("shard_1");

        XdStorageSQLResourceId id2 = new XdStorageSQLResourceId();
        id2.setTable("orders");
        id2.setDataSource("shard_2"); // Разные датасорсы

        XdStorageSQLResourceId id3 = new XdStorageSQLResourceId();
        id3.setTable("products"); // Разные таблицы
        id3.setDataSource("shard_1");

        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
    }

    @Test
    public void testEqualsAndHashCode_MutationBehavior_ShouldChangeDynamically() {
        // Тестируем граничное условие мутабельного бина: как ведут себя equals/hashCode при изменении полей на лету
        XdStorageSQLResourceId id1 = new XdStorageSQLResourceId();
        id1.setTable("logs");
        id1.setDataSource("shard_logs");

        XdStorageSQLResourceId id2 = new XdStorageSQLResourceId();
        id2.setTable("logs");
        id2.setDataSource("shard_logs");

        assertEquals(id1, id2);

        // Мутируем один из объектов
        id2.setTable("archived_logs");

        // Теперь они не должны быть равны
        assertNotEquals(id1, id2);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testEqualsAndHashCode_WithNullFields_ShouldHandleSafely() {
        // Граничное условие: поля table или dataSource (или оба) равны null.
        // Проверяем, что Objects.equals() внутри класса защищает от NullPointerException.
        XdStorageSQLResourceId id1 = new XdStorageSQLResourceId(); // оба null
        XdStorageSQLResourceId id2 = new XdStorageSQLResourceId(); // оба null

        XdStorageSQLResourceId id3 = new XdStorageSQLResourceId();
        id3.setTable("users"); // dataSource остался null

        XdStorageSQLResourceId id4 = new XdStorageSQLResourceId();
        id4.setTable("users"); // dataSource остался null

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());

        assertEquals(id3, id4);
        assertEquals(id3.hashCode(), id4.hashCode());

        assertNotEquals(id1, id3);
    }

    @Test
    public void testEqualsAndHashCode_WithEmptyStrings_ShouldDifferentiateCorrectly() {
        // Граничное условие: пустые строки вместо реальных имен.
        XdStorageSQLResourceId id1 = new XdStorageSQLResourceId();
        id1.setTable("");
        id1.setDataSource("");

        XdStorageSQLResourceId id2 = new XdStorageSQLResourceId();
        id2.setTable("");
        id2.setDataSource("");

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    public void testEquals_WithForeignObjectTypeAndNull_ShouldReturnFalseSafely() {
        // Граничное условие: сравнение с null или объектом другого класса
        XdStorageSQLResourceId id = new XdStorageSQLResourceId();
        id.setTable("users");
        id.setDataSource("main");

        assertNotEquals(null, id);
        assertNotEquals("some_string_object", id);
    }
}
