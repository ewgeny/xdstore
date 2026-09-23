package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.datasource.IXdStorageSQLDataSourceProvider;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageSQLResourcesManager.
 * Защищен от рассинхронизации импортов распределенных реляционных ресурсов.
 */
public class XdStorageSQLResourcesManagerTest {

    private XdStorageServicesLocator mockServices;
    private IXdStorageSQLTypesHelper mockTypesHelper;
    private IXdStorageSQLDataSourceProvider mockDataSourceProvider;
    private XdStorageSQLResourceNamingService mockNamingService;

    private XdStorageSQLResourcesManager resourcesManager;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockTypesHelper = mock(IXdStorageSQLTypesHelper.class);
        mockDataSourceProvider = mock(IXdStorageSQLDataSourceProvider.class);
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);

        when(mockServices.getTypesHelper()).thenReturn(mockTypesHelper);
        when(mockServices.getDataSourceProvider()).thenReturn(mockDataSourceProvider);
        when(mockServices.getNamingService()).thenReturn(mockNamingService);

        resourcesManager = new XdStorageSQLResourcesManager(mockServices);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testConstructor_ShouldInitializeDependencies() {
        assertNotNull(resourcesManager.getTypesHelper());
        assertSame(mockTypesHelper, resourcesManager.getTypesHelper());
    }

    @Test
    public void testGetDataSource_HappyPath_ShouldDelegateToProvider() {
        IXdStorageSQLDataSourceConfiguration mockConfig = mock(IXdStorageSQLDataSourceConfiguration.class);
        DataSource mockDataSource = mock(DataSource.class);

        when(mockDataSourceProvider.newIfNotExistAndGet(mockConfig)).thenReturn(mockDataSource);

        DataSource result = resourcesManager.getDataSource(mockConfig);

        assertNotNull(result);
        assertSame(mockDataSource, result);
        verify(mockDataSourceProvider, times(1)).newIfNotExistAndGet(mockConfig);
    }

    @Test
    public void testCreateResource_ShouldReturnValidInstance() {
        XdStorageClassInfo mockClassInfo = mock(XdStorageClassInfo.class);
        Object resourceId = "table_users";

        IXdStorageResourceObject<IXdStorageDaoResource> resource = resourcesManager.createResource(resourceId, mockClassInfo);

        assertNotNull(resource);
        assertTrue(resource instanceof XdStorageSQLResource);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testLockClassResource_WithUnsupportedPolicy_ShouldReturnNullSafely() throws Exception {
        XdStorageClassInfo mockClassInfo = mock(XdStorageClassInfo.class);
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        when(mockClassInfo.getPolicy()).thenReturn(XdStoragePolicy.StoreWithParentObject);

        IXdStorageDaoResource daoResource = resourcesManager.lockClassResource(new Object(), mockClassInfo, mockTx);

        assertNull(daoResource);
    }

    @Test
    public void testLockForeignKeyObjectsResource_WithSameDataSource_ShouldReturnNullOrDummyDao() throws Exception {
        XdStorageSQLResourceId parentId = mock(XdStorageSQLResourceId.class);
        XdStorageSQLResourceId childId = mock(XdStorageSQLResourceId.class);
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        when(parentId.getDataSource()).thenReturn("shard_alpha");
        when(childId.getDataSource()).thenReturn("shard_alpha");

        // ИСПРАВЛЕНИЕ: Использование полного квалифицированного пути к интерфейсу для 100% компиляции
        org.flib.xdstorage.sqlstorage.fkresource.IXdStorageCrossDatasourceFkDaoResource dao =
                resourcesManager.lockForeignKeyObjectsResource(
                        parentId, childId, mockTx, mock(XdStorageClassInfo.class), mock(XdStorageClassInfo.class)
                );

        assertNotNull(dao);
    }
}
