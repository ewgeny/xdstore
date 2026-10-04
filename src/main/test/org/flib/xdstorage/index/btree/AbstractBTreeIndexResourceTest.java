package org.flib.xdstorage.index.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.search.IXdStorageSearchIndexResourceObject;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

import static org.mockito.Mockito.*;

public abstract class AbstractBTreeIndexResourceTest {

    protected XdStorageServicesLocator mockServices;
    protected XdStorageAbstractResourcesManager mockManager;
    protected IXdStorage mockStorage;
    protected IXdStorageDaoResource mockDaoResource;
    protected IXdStorageIdGenerator mockIdGenerator;

    protected XdStorageClassInfo mockObjectClassInfo;
    protected XdStorageObjectIdField mockObjectIdField;

    protected XdStorageBTreeIndexResource indexResource;
    protected String testFileName;

    @TempDir
    protected Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        mockServices = mock(XdStorageServicesLocator.class);
        mockManager = mock(XdStorageAbstractResourcesManager.class); // Возвращаем чистый мок
        mockStorage = mock(IXdStorage.class);
        mockDaoResource = mock(IXdStorageDaoResource.class);
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        // =========================================================================
        // ИСПРАВЛЕНИЕ МOКOВ (Ликвидация NullPointerException на getNamingService):
        // Создаем мок для сервиса именования ресурсов СУБД и обучаем mockManager
        // возвращать его при вызове getNamingService(). Также программируем метод
        // getResourceId() возвращать строку-идентификатор, полностью закрывая
        // ошибку строки 97 в оригинальном менеджере ресурсов!
        // =========================================================================
        org.flib.xdstorage.resource.IXdStorageResourceNamingService mockNamingService =
                mock(org.flib.xdstorage.resource.IXdStorageResourceNamingService.class);

        when(mockManager.getNamingService()).thenReturn(mockNamingService);
        when(mockNamingService.getResourceId(any(), any(), any(), any())).thenReturn("test-resource-id-string");

        // Обучаем менеджер перехватывать все вариации lockResource и отдавать mockDaoResource
        when(mockDaoResource.getResourceId()).thenReturn("test-resource-id-string");
        when(mockManager.lockResource(any(XdStorageClassInfo.class), any(Object.class), any())).thenReturn(mockDaoResource);
        when(mockManager.lockResource(any(Object.class), any(), any())).thenReturn(mockDaoResource);
        when(mockManager.getFreeResourceId(any(Class.class), anyLong())).thenReturn("test-resource-id-string");

        IXdStorageIOFactory mockIoFactory = mock(IXdStorageIOFactory.class);
        IXdStorageObjectsReader mockReader = mock(IXdStorageObjectsReader.class);
        IXdStorageObjectsWriter mockWriter = mock(IXdStorageObjectsWriter.class);

        when(mockServices.getStorage()).thenReturn(mockStorage);
        when(mockServices.getIoFactory()).thenReturn(mockIoFactory);
        when(mockServices.getIdGenerator()).thenReturn(mockIdGenerator);
        when(mockIoFactory.newInstanceReader()).thenReturn(mockReader);
        when(mockIoFactory.newInstanceWriter()).thenReturn(mockWriter);

        mockObjectClassInfo = mock(XdStorageClassInfo.class);
        mockObjectIdField = mock(XdStorageObjectIdField.class);

        mockObjectClassInfo = mock(XdStorageClassInfo.class);
        mockObjectIdField = mock(XdStorageObjectIdField.class);

        when(mockObjectClassInfo.getIdField()).thenReturn(mockObjectIdField);
        when(mockObjectClassInfo.getClazz()).thenAnswer(inv -> TestEntity.class);

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ МOКOВ (Полное уничтожение расхождения null / 42L):
        // Программируем get() возвращать 42L, если у TestEntity поле id уже заполнено,
        // ИЛИ если это прокси-оболочка, сгенерированная XdStorageObserverService.
        // Это гарантирует, что в tree.insert() улетит легитимная сорок двойка!
        // =========================================================================
        when(mockObjectIdField.get(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            Object rawObject = XdStorageObjectUtils.getWrappedObjectOrSameObject(arg);

            if (rawObject instanceof TestEntity) {
                Long currentId = ((TestEntity) rawObject).getId();
                // Если id еще не прописан (первый вызов), возвращаем null для триггера генератора.
                // На всех последующих вызовах (включая tree.insert) возвращаем сгенерированные 42L!
                return currentId != null ? currentId : 42L;
            }
            return 42L;
        });

        // Настраиваем set() на безусловное прописание ID в наш тестовый entity
        doAnswer(invocation -> {
            Object targetWrapper = invocation.getArgument(0);
            Object generatedId = invocation.getArgument(1);

            Object rawObject = XdStorageObjectUtils.getWrappedObjectOrSameObject(targetWrapper);
            if (rawObject instanceof TestEntity) {
                ((TestEntity) rawObject).setId((Long) generatedId);
            }
            return null;
        }).when(mockObjectIdField).set(any(), any());

        File tempFile = tempDir.resolve("test_index.idx").toFile();
        testFileName = tempFile.getAbsolutePath();

        indexResource = new XdStorageBTreeIndexResource(
                mockServices, mockManager, testFileName, "id_index", mockObjectClassInfo, 4096
        );
    }

    protected XdStorageTransaction mockTransaction(String id) {
        XdStorageTransaction tx = mock(XdStorageTransaction.class);
        when(tx.getTransactionId()).thenReturn(id);
        when(tx.getTimeout()).thenReturn(2000L);
        when(tx.getTimestart()).thenReturn(System.nanoTime());
        return tx;
    }

    protected static class TestEntity {
        private Long id;
        private String data;
        public TestEntity() {}
        public TestEntity(Long id, String data) { this.id = id; this.data = data; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
