package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.IXdStorageTransactionManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractXdStorageResourceCacheTest {

    protected XdStorageServicesLocator mockLocator;
    protected IXdStorageCloner mockCloner;
    protected IXdStorage mockStorage;
    protected XdStorageObjectIdField mockField;
    protected IXdStorageTransactionManager mockTxManager;
    protected XdStorageResourceCache resourceCache;

    // Изолированная сущность для Unit-тестов
    // Конструктор без аргументов обязателен по конвенции СУБД: генератор оберток
    // наследует сущность, а скомпилированный прокси требует no-arg super().
    // @XdStorageObjectId + сеттер обязательны, чтобы MVCC-клонер переносил ID в глубокие копии
    public static class TestEntity {
        @org.flib.xdstorage.annotations.XdStorageObjectId
        private Long id;
        private String value;

        public TestEntity() {}
        public TestEntity(Long id, String value) {
            this.id = id;
            this.value = value;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public interface MockObservableWrapper extends org.flib.xdstorage.observing.IXdStorageIdObservableWrapper {
        void triggerIdSet(Object id);
    }

    @BeforeEach
    public void setUp() throws Exception {
        mockLocator = mock(XdStorageServicesLocator.class);
        mockCloner = mock(IXdStorageCloner.class);
        mockStorage = mock(IXdStorage.class);
        mockField = mock(XdStorageObjectIdField.class);
        mockTxManager = mock(IXdStorageTransactionManager.class);

        when(mockLocator.getTransactionsManager()).thenReturn(mockTxManager);
        when(mockLocator.getCloner()).thenReturn(mockCloner);
        when(mockLocator.getStorage()).thenReturn(mockStorage);
        when(mockTxManager.isTransactionAlive(any())).thenReturn(true);

        when(mockField.get(any())).thenAnswer(inv -> {
            Object obj = inv.getArgument(0);
            return obj instanceof TestEntity ? ((TestEntity) obj).getId() : null;
        });

        // =========================================================================
        // СУБД TDD-ФИКС СТEНДA: Исправляем SampleNode на TestEntity!
        // Прежний скрытый ClassCastException внутри cloneAndWrap гасил предикат,
        // из-за чего readInternal возвращал пустую коллекцию (Actual: 0).
        // =========================================================================
        when(mockCloner.cloneAndWrap(any(), any(), any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg instanceof TestEntity) {
                TestEntity orig = (TestEntity) arg;
                return new TestEntity(orig.getId(), orig.getValue());
            }
            return arg;
        });

        when(mockCloner.unwrapAndClone(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg instanceof TestEntity) {
                TestEntity orig = (TestEntity) arg;
                return new TestEntity(orig.getId(), orig.getValue());
            }
            return arg;
        });

        doAnswer(inv -> {
            TestEntity reference = inv.getArgument(0);
            TestEntity sourceNode = inv.getArgument(1);
            if (reference != null && sourceNode != null) {
                // Симулируем честное наполнение полей прокси-обертки данными
                reference.setValue(sourceNode.getValue());
            }
            return null;
        }).when(mockCloner).fillAndWrap(any(), any(), any(), any());

        resourceCache = new XdStorageResourceCache(mockLocator, TestEntity.class, mockField);
    }

    protected XdStorageTransaction createTx(String txId, long timestart) {
        XdStorageTransaction tx = mock(XdStorageTransaction.class);
        when(tx.getTransactionId()).thenReturn(txId);
        when(tx.getTimestart()).thenReturn(timestart);
        when(tx.getTimeout()).thenReturn(5000L);
        return tx;
    }
}
