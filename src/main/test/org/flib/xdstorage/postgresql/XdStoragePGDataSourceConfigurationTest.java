package org.flib.xdstorage.postgresql;

import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLDataSourceType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки JavaBean инвариантов класса XdStoragePGDataSourceConfiguration.
 */
public class XdStoragePGDataSourceConfigurationTest {

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStoragePGDataSourceConfiguration config = new XdStoragePGDataSourceConfiguration();

        config.setName("shard_01");
        config.setType(XdStorageSQLDataSourceType.CENTRAL);
        config.setServer("127.0.0.1");
        config.setPort(5432);
        config.setDatabase("xd_db");
        config.setUser("postgres");
        config.setPassword("secret");
        config.setInitialConnections(5);
        config.setMaxConnections(20);

        assertEquals("shard_01", config.getName());
        assertEquals(XdStorageSQLDataSourceType.CENTRAL, config.getType());
        assertEquals("127.0.0.1", config.getServer());
        assertEquals(5432, config.getPort());
        assertEquals("xd_db", config.getDatabase());
        assertEquals("postgres", config.getUser());
        assertEquals("secret", config.getPassword());
        assertEquals(5, config.getInitialConnections());
        assertEquals(20, config.getMaxConnections());

        // Проверяем работу утилитного метода приведения типов cast()
        XdStoragePGDataSourceConfiguration casted = config.cast();
        assertSame(config, casted);
    }

    @Test
    public void testEqualsAndHashCode_StrictlyBasedOnName() {
        XdStoragePGDataSourceConfiguration config1 = new XdStoragePGDataSourceConfiguration();
        config1.setName("main_db");
        config1.setDatabase("production");

        XdStoragePGDataSourceConfiguration config2 = new XdStoragePGDataSourceConfiguration();
        config2.setName("main_db");
        config2.setDatabase("testing"); // Разные базы данных, но одинаковые имена конфигураций

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    public void testEquals_WithNullAndForeignObject_ShouldReturnFalseSafely() {
        XdStoragePGDataSourceConfiguration config = new XdStoragePGDataSourceConfiguration();
        config.setName("db");

        assertNotEquals(null, config);
        assertNotEquals("foreign_string_type", config);
    }
}
