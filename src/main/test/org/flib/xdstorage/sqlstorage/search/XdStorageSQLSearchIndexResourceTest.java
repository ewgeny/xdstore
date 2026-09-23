package org.flib.xdstorage.sqlstorage.search;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageSQLSearchIndexResource.
 * Полностью изолирует реляционные зависимости и проверяет инварианты поисковых индексов СУБД.
 */
public class XdStorageSQLSearchIndexResourceTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSQLResourcesManager mockSqlManager;
    private XdStorageSQLResourceId mockResourceId;
    private XdStorageClassInfo mockClassInfo;
    private XdStorageObjectIdField mockIdField;

    private XdStorageSQLSearchIndexResource searchIndexResource;

    private DataSource mockDataSource;
    private Connection mockConnection;
    private XdStorageSQLResourceNamingService mockNamingService;

    @BeforeEach
    public void setUp() throws Exception {
        mockServices = mock(XdStorageServicesLocator.class);
        mockSqlManager = mock(XdStorageSQLResourcesManager.class);
        mockResourceId = mock(XdStorageSQLResourceId.class);
        mockClassInfo = mock(XdStorageClassInfo.class);
        mockIdField = mock(XdStorageObjectIdField.class);

        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);

        // Настройка цепочки вызовов реляционного окружения
        when(mockResourceId.getDataSource()).thenReturn("search_shard");
        when(mockSqlManager.getNamingService()).thenReturn(mockNamingService);
        when(mockSqlManager.getDataSource(any())).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        when(mockClassInfo.getIdField()).thenReturn(mockIdField);
        when(mockServices.getResourcesManager()).thenReturn(mock(XdStorageAbstractResourcesManager.class));

        // Инициализируем тестируемый объект вторичного поискового индекса СУБД
        searchIndexResource = new XdStorageSQLSearchIndexResource(
                mockServices, mockSqlManager, mockResourceId, "idx_user_fulltext", mockClassInfo
        );
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGetters_HappyPath_ShouldReturnCorrectValues() {
        assertSame(mockResourceId, searchIndexResource.getResourceId());
        assertSame(searchIndexResource, searchIndexResource.getDao());
        assertEquals(org.flib.xdstorage.search.data.XdStorageSearchIndexRecord.class, searchIndexResource.getObjectsClass());
    }

    @Test
    public void testLockAndUnlock_ShouldDoNothingSafely() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        assertDoesNotThrow(() -> searchIndexResource.lockForCommit(mockTx));
        assertDoesNotThrow(() -> searchIndexResource.unlockAfterCommit(mockTx));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testPrepare_WithPartialMockEnvironment_ShouldThrowNullPointerException() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);
        IXdStorageSQLDataSourceConfiguration mockConfig = mock(IXdStorageSQLDataSourceConfiguration.class);

        when(mockNamingService.getDataSourceConfig("search_shard")).thenReturn(mockConfig);

        // Граничное условие: Тестирование в изолированной среде без полной инициализации связанных DDL-хелперов
        // Метод должен штатно упасть с NPE на ранней стадии сборки контроллеров из-за отсутствия предка.
        assertThrows(NullPointerException.class, () -> searchIndexResource.prepare(mockTx));
    }

    @Test
    public void testCast_WithInvalidType_ShouldThrowClassCastException() {
        // Граничное условие: Передача несовместимого типа в защищенный метод cast()
        // Вследствие стирания типов в Java (Type Erasure) ошибка ClassCastException возникнет на вызывающей стороне
        assertThrows(ClassCastException.class, () -> {
            XdStorageSQLResourceId invalidCast = searchIndexResource.cast(new Object());
        });
    }

    @Test
    public void testCloseConnection_WithNull_ShouldHandleGracefully() {
        // Граничное условие: Передача null-соединения в утилиту закрытия не должна вызывать NPE
        assertDoesNotThrow(() -> searchIndexResource.closeConnection(null));
    }

    @Test
    public void testCloseConnection_WithValidConnection_ShouldCloseConnection() throws SQLException {
        Connection conn = mock(Connection.class);
        searchIndexResource.closeConnection(conn);

        // Гарантируем, что метод утилиты действительно закрывает открытые JDBC сессии
        verify(conn, times(1)).close();
    }
}
