package org.flib.xdstorage.sqlstorage.configuration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки инвариантов JavaBean класса XdStorageSQLRuleConfiguration.
 */
public class XdStorageSQLRuleConfigurationTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageSQLRuleConfiguration rule = new XdStorageSQLRuleConfiguration();

        rule.setType(XdStorageSQLRuleType.ADDITIONAL);
        rule.setClassName("org.flib.model.User");
        rule.setDataSource("shard_additional_01");

        assertEquals(XdStorageSQLRuleType.ADDITIONAL, rule.getType());
        assertEquals("org.flib.model.User", rule.getClassName());
        assertEquals("shard_additional_01", rule.getDataSource());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testRule_WithNullFields_ShouldHandleSafely() {
        XdStorageSQLRuleConfiguration rule = new XdStorageSQLRuleConfiguration();

        assertNull(rule.getType());
        assertNull(rule.getClassName());
        assertNull(rule.getDataSource());
    }
}
