package org.flib.xdstorage.factories;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("1. Базовые спецификации и контракты жизненного цикла Cloner")
public class XdStorageSmartClonerBasicTest extends AbstractClonerTest {

    @Test
    @DisplayName("Метод cloneAndWrap() при передаче null обязан безопасно возвращать null")
    public void testCloneAndWrap_WithNull_ShouldReturnNullSafely() throws XdStorageException {
        Object result = smartCloner.cloneAndWrap(null, mockStorage, mockTx);
        assertNull(result);
    }

    @Test
    @DisplayName("Метод unwrapAndClone() при передаче null обязан безопасно возвращать null")
    public void testUnwrapAndClone_WithNull_ShouldReturnNullSafely() throws XdStorageException {
        Object result = smartCloner.unwrapAndClone(null);
        assertNull(result);
    }

    @Test
    @DisplayName("Метод release() обязан транслировать вызов очистки контекстов в ReferencesProvider")
    public void testRelease_HappyPath_ShouldForwardCallToProvider() {
        smartCloner.release(mockTx);
        verify(mockProvider, times(1)).release(mockTx);
    }

    @Test
    @DisplayName("Контракт fillAndWrap() обязан падать с исключением при передаче null-ссылок")
    public void testFillAndWrap_WithNullArgs_ShouldThrowXdStorageException() {
        assertThrows(XdStorageException.class, () -> smartCloner.fillAndWrap(null, new Object(), mockStorage, mockTx));
        assertThrows(XdStorageException.class, () -> smartCloner.fillAndWrap(new Object(), null, mockStorage, mockTx));
    }
}
