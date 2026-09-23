package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageSqlSearchQuery.
 * Полностью учитывает инварианты суффиксов таблиц объектов и полей СУБД.
 */
public class XdStorageSqlSearchQueryTest {

    private XdStorageSQLResourceNamingService mockNamingService;
    private XdStorageClassInfo mockClassInfo;
    private XdStorageSearchIndex mockIndex;
    private IXdStorageSQLTypesHelper mockHelper;
    private XdStorageObjectIdField mockIdField;
    private XdStorageObjectField mockPrimaryField;

    private XdStorageSqlSearchQuery searchQuery;

    @BeforeEach
    public void setUp() {
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);
        mockClassInfo = mock(XdStorageClassInfo.class);
        mockIndex = mock(XdStorageSearchIndex.class);
        mockHelper = mock(IXdStorageSQLTypesHelper.class);
        mockIdField = mock(XdStorageObjectIdField.class);
        mockPrimaryField = mock(XdStorageObjectField.class);

        // Настраиваем дефолтное поведение метаданных полей и таблиц СУБД
        when(mockIdField.getName()).thenReturn("id");
        when(mockClassInfo.getIdField()).thenReturn(mockIdField);
        when(mockClassInfo.getClazz()).thenReturn((Class) String.class);
        when(mockIndex.getName()).thenReturn("idx_name");
        when(mockIndex.getPrimaryField()).thenReturn(mockPrimaryField);
        when(mockNamingService.getSearchIndexTable(any(), anyString())).thenReturn("test_index_table");

        searchQuery = new XdStorageSqlSearchQuery();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testBuild_WithSingleCriterion_ShouldGenerateCorrectPlainSql() throws XdStorageException {
        IXdStorageSqlPrimaryCriterion mockPrimary = mock(IXdStorageSqlPrimaryCriterion.class);
        IXdStorageSqlCriterion mockCriterion = mock(IXdStorageSqlCriterion.class);

        when(mockPrimary.build(any(), any(), any(), any(), anyString(), any())).thenReturn("tbl0.val = 'test'");
        when(mockCriterion.build(any(), any(), any(), any(), any())).thenReturn("tbl0.status = 1");

        searchQuery.or(mockPrimary, mockCriterion);

        String sql = searchQuery.build(mockNamingService, mockClassInfo, mockIndex, mockHelper);

        assertNotNull(sql);
        // ИСПРАВЛЕНИЕ: Проверяем базовые неизменяемые SQL токены выборки СУБД
        assertTrue(sql.contains("SELECT DISTINCT"));
        assertTrue(sql.contains("tbl0.id"));
        assertTrue(sql.contains("WHERE tbl0.val = 'test' AND tbl0.status = 1"));
        assertFalse(sql.contains("UNION"));
    }

    @Test
    public void testBuild_WithMultipleCriteria_ShouldGenerateUnionSql() throws XdStorageException {
        IXdStorageSqlPrimaryCriterion mockPrimary1 = mock(IXdStorageSqlPrimaryCriterion.class);
        IXdStorageSqlPrimaryCriterion mockPrimary2 = mock(IXdStorageSqlPrimaryCriterion.class);

        when(mockPrimary1.build(any(), any(), any(), any(), anyString(), any())).thenReturn("tbl0.val = 'A'");
        when(mockPrimary2.build(any(), any(), any(), any(), anyString(), any())).thenReturn("tbl0.val = 'B'");

        searchQuery.or(mockPrimary1, null);
        searchQuery.or(mockPrimary2, null);

        String sql = searchQuery.build(mockNamingService, mockClassInfo, mockIndex, mockHelper);

        assertNotNull(sql);
        assertTrue(sql.startsWith("SELECT DISTINCT x.id FROM ("));
        assertTrue(sql.contains("UNION"));
        assertTrue(sql.endsWith(") x"));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testBuild_WithEmptyQuery_ShouldReturnEmptyStringSafely() throws XdStorageException {
        String sql = searchQuery.build(mockNamingService, mockClassInfo, mockIndex, mockHelper);

        assertNotNull(sql);
        assertTrue(sql.isEmpty());
    }

    @Test
    public void testBuild_WithNullCriteria_ShouldGenerateSqlWithoutWhereClause() throws XdStorageException {
        searchQuery.or(null, null);

        String sql = searchQuery.build(mockNamingService, mockClassInfo, mockIndex, mockHelper);

        assertNotNull(sql);
        assertTrue(sql.contains("SELECT DISTINCT"));
        assertTrue(sql.contains("tbl0.id"));
        // ИСПРАВЛЕНИЕ: Так как критерии null, оператора WHERE в результирующем SQL быть не должно
        assertFalse(sql.contains("WHERE"));
    }

    @Test
    public void testBuild_WhenInternalErrorOccurs_ShouldThrowXdStorageException() {
        when(mockClassInfo.getIdField()).thenThrow(new NullPointerException("Фатальный сбой метакаталога"));
        searchQuery.or(mock(IXdStorageSqlPrimaryCriterion.class), null);
        searchQuery.or(mock(IXdStorageSqlPrimaryCriterion.class), null);

        assertThrows(XdStorageException.class, () -> {
            searchQuery.build(mockNamingService, mockClassInfo, mockIndex, mockHelper);
        });
    }
}
