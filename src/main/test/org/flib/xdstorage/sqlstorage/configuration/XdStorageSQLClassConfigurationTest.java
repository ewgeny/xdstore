package org.flib.xdstorage.sqlstorage.configuration;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageSQLClassConfiguration.
 * Проверяет контракты мутации, выборочного клонирования и кастомного равенства сущностей.
 */
public class XdStorageSQLClassConfigurationTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageSQLClassConfiguration config = new XdStorageSQLClassConfiguration();
        List<XdStorageSQLRuleConfiguration> rules = new ArrayList<>();

        config.setCl(String.class);
        config.setMultiple(true);
        config.setParentDataSource(true);
        config.setDataSource("pg_shard_0");
        config.setTable("users");
        config.setRules(rules);

        assertEquals(String.class, config.getCl());
        assertTrue(config.isMultiple());
        assertTrue(config.isParentDataSource());
        assertEquals("pg_shard_0", config.getDataSource());
        assertEquals("users", config.getTable());
        assertSame(rules, config.getRules());
    }

    @Test
    public void testEqualsAndHashCode_BasedStrictlyOnTableAndDataSource() {
        XdStorageSQLClassConfiguration config1 = new XdStorageSQLClassConfiguration();
        config1.setTable("books");
        config1.setDataSource("main");
        config1.setCl(String.class); // разные классы

        XdStorageSQLClassConfiguration config2 = new XdStorageSQLClassConfiguration();
        config2.setTable("books");
        config2.setDataSource("main");
        config2.setCl(Integer.class); // разные классы

        // Проверяем инвариант СУБД: равенство зависит только от таблицы и датасорса
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    public void testClone_ShouldPerformSelectiveCopy() {
        XdStorageSQLClassConfiguration original = new XdStorageSQLClassConfiguration();
        original.setCl(String.class);
        original.setDataSource("shard_1");
        original.setTable("orders");
        original.setMultiple(true); // это поле не должно клонироваться

        XdStorageSQLClassConfiguration cloned = (XdStorageSQLClassConfiguration) original.clone();

        assertNotNull(cloned);
        assertNotSame(original, cloned);

        // Проверяем скопированные поля
        assertEquals(original.getCl(), cloned.getCl());
        assertEquals(original.getDataSource(), cloned.getDataSource());
        assertEquals(original.getTable(), cloned.getTable());

        // Проверяем, что флаги сброшены в дефолт (специфика реализации clone)
        assertFalse(cloned.isMultiple());
        assertNull(cloned.getRules());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testEqualsAndHashCode_WithNullFields_ShouldHandleSafely() {
        XdStorageSQLClassConfiguration config1 = new XdStorageSQLClassConfiguration();
        XdStorageSQLClassConfiguration config2 = new XdStorageSQLClassConfiguration();

        // Оба объекта имеют null в полях table и dataSource
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());

        config1.setTable("nodes");
        assertNotEquals(config1, config2);
    }

    @Test
    public void testEquals_WithNullAndForeignObject_ShouldReturnFalseSafely() {
        XdStorageSQLClassConfiguration config = new XdStorageSQLClassConfiguration();

        assertNotEquals(null, config);
        assertNotEquals("foreign_string_type", config);
    }

    @Test
    public void testClone_WithAllNullFields_ShouldNotThrowException() {
        XdStorageSQLClassConfiguration original = new XdStorageSQLClassConfiguration();

        // Убеждаемся, что пустой незаполненный конфигуратор клонируется без NPE
        assertDoesNotThrow(() -> {
            XdStorageSQLClassConfiguration cloned = (XdStorageSQLClassConfiguration) original.clone();
            assertNull(cloned.getCl());
            assertNull(cloned.getDataSource());
            assertNull(cloned.getTable());
        });
    }
}
