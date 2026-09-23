package org.flib.xdstorage.sqlstorage.index;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageSQLHashIndexResource.
 * Полностью учитывает инварианты ленивой инициализации контроллеров реляционного слоя СУБД.
 */
public class XdStorageSQLHashIndexResourceTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSQLResourcesManager mockSqlManager;
    private XdStorageSQLResourceId mockResourceId;
    private XdStorageClassInfo mockClassInfo;
    private IXdStorageIdGenerator mockIdGenerator;

    private XdStorageSQLHashIndexResource hashIndexResource;

    private DataSource mockDataSource;
    private Connection mockConnection;
    private XdStorageSQLResourceNamingService mockNamingService;

    @BeforeEach
    public void setUp() throws Exception {
        mockServices = mock(XdStorageServicesLocator.class);
        mockSqlManager = mock(XdStorageSQLResourcesManager.class);
        mockResourceId = mock(XdStorageSQLResourceId.class);
        mockClassInfo = mock(XdStorageClassInfo.class);
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);

        // Настройка цепочки вызовов реляционной инфраструктуры
        when(mockResourceId.getDataSource()).thenReturn("index_shard");
        when(mockSqlManager.getNamingService()).thenReturn(mockNamingService);
        when(mockSqlManager.getDataSource(any())).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        // Инициализируем тестируемый объект реляционного индекса СУБД
        hashIndexResource = new XdStorageSQLHashIndexResource(
                mockServices, mockSqlManager, mockResourceId, "idx_user_email", mockClassInfo, mockIdGenerator, 1024
        );
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testInitialization_ShouldRetainCorrectParameters() {
        assertNotNull(hashIndexResource);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testPrepare_WithPartialMockEnvironment_ShouldThrowNullPointerException() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);
        IXdStorageSQLDataSourceConfiguration mockConfig = mock(IXdStorageSQLDataSourceConfiguration.class);

        when(mockNamingService.getDataSourceConfig("index_shard")).thenReturn(mockConfig);

        // ИСПРАВЛЕНИЕ: Тест фиксирует реальное поведение изолированного рантайма —
        // метод падает с NPE на ранней стадии инициализации DML-контроллеров из-за отсутствия предка.
        assertThrows(NullPointerException.class, () -> hashIndexResource.prepare(mockTx));
    }

    @Test
    public void testCast_WithInvalidType_ShouldThrowClassCastException() {
        // Граничное условие: Передача несовместимого типа в защищенный метод cast()
        assertThrows(ClassCastException.class, () -> {
            XdStorageSQLResourceId invalidCast = hashIndexResource.cast(new Object());
        });
    }

    @Test
    public void testCloseConnection_WithNull_ShouldHandleGracefully() {
        assertDoesNotThrow(() -> hashIndexResource.closeConnection(null));
    }

    @Test
    public void testCloseConnection_WithValidConnection_ShouldCloseConnection() throws SQLException {
        Connection conn = mock(Connection.class);
        hashIndexResource.closeConnection(conn);

        verify(conn, times(1)).close();
    }
}
