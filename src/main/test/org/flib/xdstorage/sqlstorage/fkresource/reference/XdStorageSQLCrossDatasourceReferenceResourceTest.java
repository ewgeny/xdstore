package org.flib.xdstorage.sqlstorage.fkresource.reference;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Полностью исправленный JUnit 5 тест для класса XdStorageSQLCrossDatasourceReferenceResource.
 * Учитывает инварианты стирания типов Java Type Erasure для проверки метода cast().
 */
public class XdStorageSQLCrossDatasourceReferenceResourceTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSQLResourceId mockResourceId;
    private XdStorageClassInfo mockParentClassInfo;
    private XdStorageClassInfo mockChildClassInfo;

    private XdStorageSQLCrossDatasourceReferenceResource referenceResource;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockResourceId = mock(XdStorageSQLResourceId.class);
        mockParentClassInfo = mock(XdStorageClassInfo.class);
        mockChildClassInfo = mock(XdStorageClassInfo.class);

        when(mockChildClassInfo.getClazz()).thenReturn((Class) String.class);

        referenceResource = new XdStorageSQLCrossDatasourceReferenceResource(
                mockServices, mockResourceId, mockParentClassInfo, mockChildClassInfo
        );
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGetters_HappyPath_ShouldReturnCorrectMetadata() {
        assertSame(mockResourceId, referenceResource.getResourceId());
        assertEquals(String.class, referenceResource.getObjectsClass());
        assertSame(referenceResource, referenceResource.getDao());
    }

    @Test
    public void testLockAndUnlock_ShouldDoNothingSafely() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        assertDoesNotThrow(() -> referenceResource.lockForCommit(mockTx));
        assertDoesNotThrow(() -> referenceResource.unlockAfterCommit(mockTx));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testGetObjectsCount_ShouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            referenceResource.getObjectsCount();
        });
    }

    @Test
    public void testCast_WithInvalidType_ShouldThrowClassCastException() {
        // ИСПРАВЛЕНИЕ: Тест фиксирует реальное поведение рантайма Java —
        // из-за стирания типов приведение происходит на вызывающей стороне, генерируя ClassCastException.
        assertThrows(ClassCastException.class, () -> {
            XdStorageSQLResourceId invalidCast = referenceResource.cast(new Object());
        });
    }
}
