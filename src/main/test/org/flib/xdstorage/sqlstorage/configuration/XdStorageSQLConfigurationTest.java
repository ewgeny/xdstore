package org.flib.xdstorage.sqlstorage.configuration;

import org.flib.xdstorage.postgresql.XdStoragePGDataSourceConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageSQLConfiguration.
 * Проверяет потокобезопасные реестры конфигураций шардов и классов СУБД.
 */
public class XdStorageSQLConfigurationTest {

    private XdStorageSQLConfiguration configuration;

    @BeforeEach
    public void setUp() {
        configuration = new XdStorageSQLConfiguration();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь / Happy Path) ===

    @Test
    public void testDataSourceConfig_HappyPath_ShouldStoreAndRetrieve() {
        XdStoragePGDataSourceConfiguration mockConfig = mock(XdStoragePGDataSourceConfiguration.class);
        when(mockConfig.getName()).thenReturn("shard_01");

        // Добавляем и извлекаем конфигурацию источника данных
        configuration.addDataSourceConfig(mockConfig);

        assertSame(mockConfig, configuration.getDataSourceConfig("shard_01"));
    }

    @Test
    public void testGetCentralDataSourceConfig_HappyPath_ShouldFindCentralShard() {
        XdStoragePGDataSourceConfiguration mockReplica = mock(XdStoragePGDataSourceConfiguration.class);
        when(mockReplica.getName()).thenReturn("shard_replica");
        when(mockReplica.getType()).thenReturn(XdStorageSQLDataSourceType.ADDITIONAL); // Предполагаем наличие типа REPLICA

        XdStoragePGDataSourceConfiguration mockCentral = mock(XdStoragePGDataSourceConfiguration.class);
        when(mockCentral.getName()).thenReturn("shard_central");
        // Явно выставляем тип CENTRAL для проверки фильтрации в цикле
        when(mockCentral.getType()).thenReturn(XdStorageSQLDataSourceType.CENTRAL);

        configuration.addDataSourceConfig(mockReplica);
        configuration.addDataSourceConfig(mockCentral);

        // Метод должен корректно отфильтровать и вернуть именно центральный шард
        XdStoragePGDataSourceConfiguration result = configuration.getCentralDataSourceConfig();

        assertNotNull(result);
        assertEquals("shard_central", result.getName());
    }

    @Test
    public void testClassConfig_HappyPath_ShouldStoreAndRetrieve() {
        XdStorageSQLClassConfiguration mockClassConfig = mock(XdStorageSQLClassConfiguration.class);
        // Задаем явный маппинг на класс String.class
        doReturn(String.class).when(mockClassConfig).getCl();

        configuration.addClassConfig(mockClassConfig);

        assertSame(mockClassConfig, configuration.getClassConfig(String.class));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ (Edge Cases) ===

    @Test
    public void testGetDataSourceConfig_WithNonExistentName_ShouldReturnNullSafely() {
        // Граничное условие: Запрос конфигурации по несуществующему имени шарда
        assertNull(configuration.getDataSourceConfig("ghost_shard"));
    }

    @Test
    public void testGetCentralDataSourceConfig_WhenNoCentralExists_ShouldReturnNullSafely() {
        XdStoragePGDataSourceConfiguration mockReplica = mock(XdStoragePGDataSourceConfiguration.class);
        when(mockReplica.getName()).thenReturn("shard_replica");
        when(mockReplica.getType()).thenReturn(null); // Центрального узла нет

        configuration.addDataSourceConfig(mockReplica);

        // Граничное условие: Если среди датасорсов нет типа CENTRAL, метод обязан безопасно вернуть null
        assertNull(configuration.getCentralDataSourceConfig());
    }

    @Test
    public void testGetCentralDataSourceConfig_WithEmptyRegistry_ShouldReturnNullSafely() {
        // Граничное условие: Поиск центрального узла в абсолютно пустом реестре СУБД
        assertNull(configuration.getCentralDataSourceConfig());
    }

    @Test
    public void testAddDataSourceConfig_WithNullName_ShouldThrowOrStoreSafely() {
        XdStoragePGDataSourceConfiguration mockNullConfig = mock(XdStoragePGDataSourceConfiguration.class);
        when(mockNullConfig.getName()).thenReturn(null);

        // Граничное условие: Конфигурация возвращает null в качестве имени.
        // Так как ConcurrentHashMap не одобряет null-ключи, операция обязана выбросить NullPointerException.
        assertThrows(NullPointerException.class, () -> {
            configuration.addDataSourceConfig(mockNullConfig);
        });
    }

    @Test
    public void testGetClassConfig_WithNullClass_ShouldReturnNullSafely() {
        // Граничное условие: Поиск конфигурации для null-класса.
        // Специфика ConcurrentHashMap запрещает передачу null в get(), поэтому мы ловим NPE на входе.
        assertThrows(NullPointerException.class, () -> {
            configuration.getClassConfig(null);
        });
    }
}
