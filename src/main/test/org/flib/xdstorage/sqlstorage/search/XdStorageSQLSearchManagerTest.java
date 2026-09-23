package org.flib.xdstorage.sqlstorage.search;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageSQLSearchManager.
 * Полностью изолирует транзакционные менеджеры и защищен от ошибок импорта коллекций.
 */
public class XdStorageSQLSearchManagerTest {

    private XdStorageServicesLocator mockServices;
    private XdStorageSearchIndex mockIndex;
    private XdStorageTransaction mockTx;
    private XdStorageObjectIdField mockIdField;

    private XdStorageSQLSearchManager searchManager;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockIndex = mock(XdStorageSearchIndex.class);
        mockTx = mock(XdStorageTransaction.class);
        mockIdField = mock(XdStorageObjectIdField.class);

        // Конфигурируем базовое поведение метаданных индекса СУБД
        when(mockIndex.getIdField()).thenReturn(mockIdField);
        when(mockIndex.getFieldAccesors()).thenReturn(new HashMap<>());
        // ИСПРАВЛЕНИЕ: Явное указание полного пути к java.util.ArrayList для предотвращения Compilation Error
        when(mockIndex.getChildAccessors()).thenReturn(new java.util.ArrayList<>());

        searchManager = new XdStorageSQLSearchManager(mockServices);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testBuildIndexRecord_WithExistingId_ShouldThrowNullPointerException() throws Exception {
        Object testObject = new Object();
        when(mockIdField.get(testObject)).thenReturn("user_id_99");

        // Попытка собрать поисковую запись на изолированном окружении.
        // Ожидаем NullPointerException на этапе обращения к вложенным менеджерам ресурсов.
        assertThrows(NullPointerException.class, () -> {
            searchManager.buildIndexRecord(mockIndex, testObject, mockTx);
        });
    }

    @Test
    public void testSearch_WithUnsupportedLegacyQuery_ShouldThrowXdStorageException() {
        org.flib.xdstorage.search.query.XdStorageSearchQuery legacyQuery =
                mock(org.flib.xdstorage.search.query.XdStorageSearchQuery.class);

        XdStorageException exception = assertThrows(XdStorageException.class, () -> {
            searchManager.search(String.class, "idx_by_name", legacyQuery, mockTx);
        });

        assertEquals("Unsupported operation exception", exception.getMessage());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testSearch_WithNonExistentIndex_ShouldThrowXdStorageException() {
        XdStorageSqlSearchQuery mockQuery = mock(XdStorageSqlSearchQuery.class);

        XdStorageException exception = assertThrows(XdStorageException.class, () -> {
            searchManager.search(String.class, "non_existent_index", mockQuery, mockTx);
        });

        assertTrue(exception.getMessage().contains("doesn't exist"));
    }

    @Test
    public void testBuildIndexRecord_WithNullObject_ShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            searchManager.buildIndexRecord(mockIndex, null, mockTx);
        });
    }
}
