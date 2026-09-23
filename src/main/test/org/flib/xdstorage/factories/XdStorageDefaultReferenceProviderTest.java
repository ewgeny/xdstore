package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageDefaultReferenceProvider.
 */
public class XdStorageDefaultReferenceProviderTest {

    private IXdStorage mockStorage;
    private IXdStorageTransaction mockTx;
    private XdStorageDefaultReferenceProvider referenceProvider;

    @BeforeEach
    public void setUp() {
        mockStorage = mock(IXdStorage.class);
        mockTx = mock(IXdStorageTransaction.class);

        // Настраиваем дефолтный возврат ID транзакции для кэша
        when(mockTx.getTransactionId()).thenReturn("tx_test_123");

        referenceProvider = new XdStorageDefaultReferenceProvider(mockStorage);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGetReference_WhenCacheIsEmpty_ShouldReturnNullSafely() {
        // Проверяем, что при отсутствии данных в кэше метод не падает с NPE, а возвращает null
        IXdStorageSimpleWrapper ref = referenceProvider.getReference(String.class, "id_01", mockTx);
        assertNull(ref);
    }

    @Test
    public void testRelease_HappyPath_ShouldClearTransactionCache() {
        XdStorageTransaction mockRealTx = mock(XdStorageTransaction.class);
        when(mockRealTx.getTransactionId()).thenReturn("tx_test_123");

        // Вызов очистки кэша не должен приводить к исключениям
        assertDoesNotThrow(() -> referenceProvider.release(mockRealTx));

        IXdStorageSimpleWrapper ref = referenceProvider.getReference(String.class, "id_01", mockTx);
        assertNull(ref);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testGetReference_WithNullArgs_ShouldThrowNullPointerException() {
        // Граничное условие: передача null вместо транзакции
        assertThrows(NullPointerException.class, () -> {
            referenceProvider.getReference(String.class, "id_01", null);
        });
    }
}
