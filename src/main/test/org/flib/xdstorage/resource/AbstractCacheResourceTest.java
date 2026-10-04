package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.*;

public abstract class AbstractCacheResourceTest {

    protected XdStorageServicesLocator mockServices;
    protected IXdStorageCloner mockCloner;
    protected XdStorageObjectIdField mockIdField;
    protected IXdStorage mockStorage;
    protected XdStorageResourceCache resourceCache;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockCloner = mock(IXdStorageCloner.class);
        mockIdField = mock(XdStorageObjectIdField.class);
        mockStorage = mock(IXdStorage.class);

        when(mockServices.getCloner()).thenReturn(mockCloner);
        when(mockServices.getStorage()).thenReturn(mockStorage);

        // Стратегия генерации ID по умолчанию — хэш-код объекта
        when(mockIdField.get(any())).thenAnswer(invocation -> (long) invocation.getArgument(0).hashCode());

        resourceCache = new XdStorageResourceCache(mockServices, TestEntity.class, mockIdField);
    }

    protected XdStorageTransaction mockTransaction(String txId) {
        XdStorageTransaction tx = mock(XdStorageTransaction.class);
        when(tx.getTransactionId()).thenReturn(txId);
        when(tx.getTimestart()).thenReturn(System.nanoTime());
        when(tx.getTimeout()).thenReturn(3000L);
        return tx;
    }

    protected static class TestEntity {
        private final String data;
        public TestEntity(String data) { this.data = data; }
        @Override
        public String toString() { return "TestEntity{" + data + "}"; }
    }
}
