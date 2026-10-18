package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Операторы критериев IXdStorageCriterionOperator")
public class XdStorageCriterionOperatorsTest {

    private static XdStorageSearchIndexRecord recordWithValue(final String fieldName, final Object value) {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();
        record.setFieldValue(fieldName, value);
        return record;
    }

    // =========================================================================
    // IS_NULL
    // =========================================================================

    @Test
    @DisplayName("IS_NULL: запись с null-полем проходит, с непустым - нет")
    public void testIsNullOnRecord() {
        assertTrue(IXdStorageCriterionOperator.IS_NULL.passed(recordWithValue("name", null), "name", "ignored"));
        assertFalse(IXdStorageCriterionOperator.IS_NULL.passed(recordWithValue("name", "alpha"), "name", "ignored"));
        // значение критерия не влияет на исход
        assertTrue(IXdStorageCriterionOperator.IS_NULL.passed(recordWithValue("name", null), "name", null));
    }

    @Test
    @DisplayName("IS_NULL: вариант над значением")
    public void testIsNullOnValue() {
        assertTrue(IXdStorageCriterionOperator.IS_NULL.passed(null, "x"));
        assertFalse(IXdStorageCriterionOperator.IS_NULL.passed("alpha", "x"));
    }

    // =========================================================================
    // IS_NOT_NULL
    // =========================================================================

    @Test
    @DisplayName("IS_NOT_NULL: зеркален IS_NULL на записи и на значении")
    public void testIsNotNull() {
        assertTrue(IXdStorageCriterionOperator.IS_NOT_NULL.passed(recordWithValue("name", "alpha"), "name", null));
        assertFalse(IXdStorageCriterionOperator.IS_NOT_NULL.passed(recordWithValue("name", null), "name", null));

        assertTrue(IXdStorageCriterionOperator.IS_NOT_NULL.passed("alpha", null));
        assertFalse(IXdStorageCriterionOperator.IS_NOT_NULL.passed(null, null));
    }

    // =========================================================================
    // EQUALS
    // =========================================================================

    @Test
    @DisplayName("EQUALS: null эквивалентен только null")
    public void testEqualsNullSemantics() {
        assertTrue(IXdStorageCriterionOperator.EQUALS.passed(null, null));
        assertFalse(IXdStorageCriterionOperator.EQUALS.passed(null, "alpha"));
        assertFalse(IXdStorageCriterionOperator.EQUALS.passed("alpha", null));
    }

    @ParameterizedTest(name = "EQUALS: ''{0}'' == ''{1}'' -> {2}")
    @CsvSource({
            "alpha, alpha, true",
            "alpha, beta,  false",
            "alpha, ALPHA, false",
            "42, 42, true"
    })
    @DisplayName("EQUALS: табличные значения (регистр значим)")
    public void testEqualsTable(final String left, final String right, final boolean expected) {
        assertEquals(expected, IXdStorageCriterionOperator.EQUALS.passed(left, right));
    }

    @Test
    @DisplayName("EQUALS: чтение значения из поля записи")
    public void testEqualsOnRecord() {
        assertTrue(IXdStorageCriterionOperator.EQUALS.passed(recordWithValue("name", "alpha"), "name", "alpha"));
        assertFalse(IXdStorageCriterionOperator.EQUALS.passed(recordWithValue("name", "alpha"), "name", "beta"));
        // отсутствие поля в записи эквивалентно null
        assertTrue(IXdStorageCriterionOperator.EQUALS.passed(new XdStorageSearchIndexRecord(), "name", null));
        assertFalse(IXdStorageCriterionOperator.EQUALS.passed(new XdStorageSearchIndexRecord(), "name", "alpha"));
    }

    // =========================================================================
    // NOT_EQUALS
    // =========================================================================

    @Test
    @DisplayName("NOT_EQUALS: строгая инверсия EQUALS, включая null-семантику")
    public void testNotEquals() {
        assertFalse(IXdStorageCriterionOperator.NOT_EQUALS.passed(null, null));
        assertTrue(IXdStorageCriterionOperator.NOT_EQUALS.passed(null, "alpha"));
        assertTrue(IXdStorageCriterionOperator.NOT_EQUALS.passed("alpha", "beta"));
        assertFalse(IXdStorageCriterionOperator.NOT_EQUALS.passed("alpha", "alpha"));

        assertTrue(IXdStorageCriterionOperator.NOT_EQUALS.passed(recordWithValue("name", "alpha"), "name", "beta"));
        assertFalse(IXdStorageCriterionOperator.NOT_EQUALS.passed(recordWithValue("name", "alpha"), "name", "alpha"));
    }

    // =========================================================================
    // CONTAINS
    // =========================================================================

    @Test
    @DisplayName("CONTAINS: подстрока, регистр значим")
    public void testContains() {
        assertTrue(IXdStorageCriterionOperator.CONTAINS.passed("battlecruiser", "cruis"));
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed("battlecruiser", "CRUIS"));
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed("battlecruiser", "zzz"));
        // пустая подстрока содержится в любой строке (контракт String.contains)
        assertTrue(IXdStorageCriterionOperator.CONTAINS.passed("abc", ""));
        assertTrue(IXdStorageCriterionOperator.CONTAINS.passed("abc", "abc"));
    }

    @Test
    @DisplayName("CONTAINS: нестроковые и null-значения не проходят")
    public void testContainsNonStringAndNulls() {
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed(null, "a"));
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed("abc", null));
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed(42, "4"));
        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed("42", 42));

        assertFalse(IXdStorageCriterionOperator.CONTAINS.passed(recordWithValue("name", null), "name", "a"));
        assertTrue(IXdStorageCriterionOperator.CONTAINS.passed(recordWithValue("name", "alpha"), "name", "alp"));
    }
}
