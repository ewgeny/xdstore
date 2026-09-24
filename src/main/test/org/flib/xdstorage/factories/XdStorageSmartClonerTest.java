package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Расширенный комплект JUnit 5 тестов для проверки транзакционного клонера XdStorageSmartCloner.
 */
public class XdStorageSmartClonerTest {

    private IXdStorageReferenceProvider referenceProvider;
    private XdStorageSmartCloner smartCloner;

    // Тестовые классы с циклической зависимостью для выявления StackOverflowError
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class CyclicParent {
        @XdStorageObjectId
        private Long id;
        private CyclicChild child;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public CyclicChild getChild() { return child; }
        public void setChild(CyclicChild child) { this.child = child; }
    }

    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class CyclicChild {
        @XdStorageObjectId
        private Long id;
        private CyclicParent parent;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public CyclicParent getParent() { return parent; }
        public void setParent(CyclicParent parent) { this.parent = parent; }
    }

    @BeforeEach
    public void setUp() {
        // Используем реальную встроенную реализацию провайдера ссылок для честной проверки кэширования
        IXdStorage mockStorage = mock(IXdStorage.class);
        referenceProvider = new XdStorageDefaultReferenceProvider(mockStorage);
        smartCloner = new XdStorageSmartCloner(referenceProvider);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testCloneAndWrap_WithNull_ShouldReturnNullSafely() throws XdStorageException {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);

        Object result = smartCloner.cloneAndWrap(null, mockStorage, mockTx);
        assertNull(result);
    }

    @Test
    public void testUnwrapAndClone_WithNull_ShouldReturnNullSafely() throws XdStorageException {
        Object result = smartCloner.unwrapAndClone(null);
        assertNull(result);
    }

    @Test
    public void testRelease_HappyPath_ShouldForwardCallToProvider() {
        IXdStorageReferenceProvider mockProvider = mock(IXdStorageReferenceProvider.class);
        XdStorageSmartCloner clonerWithMock = new XdStorageSmartCloner(mockProvider);
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        clonerWithMock.release(mockTx);

        verify(mockProvider, times(1)).release(mockTx);
    }

    // === 2. ПРОДВИНУТЫЕ АЛГОРИТМИЧЕСКИЕ ТЕСТЫ (ОХОТА НА STACKOVERFLOW) ===

    @Test
    public void testCloneAndWrap_WithCircularDependency_ShouldNotThrowStackOverflow() throws Exception {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        when(mockTx.getTransactionId()).thenReturn("tx_cyclic_123");

        // Строим перекрестный граф зацикленных данных
        CyclicParent parent = new CyclicParent();
        parent.setId(1L);

        CyclicChild child = new CyclicChild();
        child.setId(2L);

        parent.setChild(child);
        child.setParent(parent); // Зациклили!

        // Проверяем инвариант безопасности ORM: глубокое копирование графа объектов
        // обязано бесшовно разрулить циклическую связь без ухода в StackOverflowError!
        assertDoesNotThrow(() -> {
            Object result = smartCloner.cloneAndWrap(parent, mockStorage, mockTx);
            assertNotNull(result);
        }, "Критический баг! Клонер уходит в бесконечную рекурсию при обработке циклических ссылок!");
    }
}
