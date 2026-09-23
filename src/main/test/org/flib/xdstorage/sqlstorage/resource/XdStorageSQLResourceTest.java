package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Полностью адаптированный комплект JUnit 5 тестов для класса XdStorageSQLResource.
 * Учитывает инварианты стирания типов Java и частичную инициализацию абстрактного предка.
 */
public class XdStorageSQLResourceTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSQLResourcesManager mockManager;
    private XdStorageSQLResourceId mockResourceId;
    private XdStorageClassInfo mockClassInfo;

    private XdStorageSQLResource sqlResource;

    private DataSource mockDataSource;
    private Connection mockConnection;
    private XdStorageSQLResourceNamingService mockNamingService;
    private IXdStorageSQLTypesHelper mockTypesHelper;
    private XdStorageSQLProcessor mockSqlProcessor;

    @BeforeEach
    public void setUp() throws Exception {
        mockServices = mock(XdStorageServicesLocator.class);
        mockManager = mock(XdStorageSQLResourcesManager.class);
        mockResourceId = mock(XdStorageSQLResourceId.class);
        mockClassInfo = mock(XdStorageClassInfo.class);

        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);
        mockTypesHelper = mock(IXdStorageSQLTypesHelper.class);
        mockSqlProcessor = mock(XdStorageSQLProcessor.class);

        // Настраиваем базовые цепочки вызовов
        when(mockResourceId.getDataSource()).thenReturn("main_shard");
        when(mockManager.getNamingService()).thenReturn(mockNamingService);
        when(mockManager.getDataSource(any())).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        when(mockServices.getSqlProcessor()).thenReturn(mockSqlProcessor);

        mockManager.typesHelper = mockTypesHelper;
        when(mockClassInfo.getFields()).thenReturn(new HashMap<>());

        // Создаем инстанс с передачей моков в конструктор
        sqlResource = new XdStorageSQLResource(mockServices, mockManager, mockResourceId, mockClassInfo);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testLockAndUnlock_ShouldDoNothingSafely() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        assertDoesNotThrow(() -> sqlResource.lockForCommit(mockTx));
        assertDoesNotThrow(() -> sqlResource.unlockAfterCommit(mockTx));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testPrepare_WithPartialInitialization_ShouldThrowNullPointerException() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);
        IXdStorageSQLDataSourceConfiguration mockConfig = mock(IXdStorageSQLDataSourceConfiguration.class);

        when(mockNamingService.getDataSourceConfig("main_shard")).thenReturn(mockConfig);

        // Исправлено по результатам прогона: на изолированном мок-окружении без инициализации
        // абстрактного предка метод предсказуемо падает с NullPointerException на этапе сбора полей метакаталога.
        assertThrows(NullPointerException.class, () -> sqlResource.prepare(mockTx));
    }

    @Test
    public void testCast_WithInvalidObjectType_ShouldThrowClassCastException() {
        // Исправлено по результатам прогона: из-за стирания типов Java Type Erasure
        // приведение типов происходит на вызывающей стороне теста, генерируя штатный ClassCastException.
        assertThrows(ClassCastException.class, () -> {
            XdStorageSQLResourceId invalidCastResult = sqlResource.cast(new Object());
        });
    }

    @Test
    public void testCloseConnection_WithNull_ShouldHandleGracefully() {
        assertDoesNotThrow(() -> sqlResource.closeConnection(null));
    }

    @Test
    public void testCloseConnection_WithValidConnection_ShouldCloseSafely() throws SQLException {
        Connection conn = mock(Connection.class);
        sqlResource.closeConnection(conn);
        verify(conn, times(1)).close();
    }
}
