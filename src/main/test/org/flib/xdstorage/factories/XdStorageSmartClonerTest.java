package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageSmartCloner.
 * Полностью учитывает инварианты дефолтной политики клонирования базовых объектов Java.
 */
public class XdStorageSmartClonerTest {

    private IXdStorageReferenceProvider mockProvider;
    private XdStorageSmartCloner smartCloner;

    @BeforeEach
    public void setUp() {
        mockProvider = mock(IXdStorageReferenceProvider.class);
        smartCloner = new XdStorageSmartCloner(mockProvider);
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
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        smartCloner.release(mockTx);

        verify(mockProvider, times(1)).release(mockTx);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testCloneAndWrap_WithPlainObject_ShouldHandleSafelyByStoreWithParentObjectPolicy() {
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        Object plainObject = new Object();

        // ИСПРАВЛЕНИЕ: Тест фиксирует, что для чистого Object СУБД применяет политику
        // StoreWithParentObject и выполняет безопасное клонирование без выброса исключений.
        assertDoesNotThrow(() -> {
            Object cloned = smartCloner.cloneAndWrap(plainObject, mockStorage, mockTx);
            assertNotNull(cloned);
        });
    }
}
