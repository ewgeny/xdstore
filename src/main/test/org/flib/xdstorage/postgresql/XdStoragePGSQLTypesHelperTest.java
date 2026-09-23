package org.flib.xdstorage.postgresql;

import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectFieldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStoragePGSQLTypesHelper.
 */
public class XdStoragePGSQLTypesHelperTest {

    private XdStoragePGSQLTypesHelper typesHelper;

    @BeforeEach
    public void setUp() {
        typesHelper = new XdStoragePGSQLTypesHelper();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGetSQLType_WithStandardClasses_ShouldReturnPostgresTypes() {
        assertEquals("VARCHAR(255)", typesHelper.getSQLType(String.class));
        assertEquals("INTEGER", typesHelper.getSQLType(Integer.class));
        assertEquals("INTEGER", typesHelper.getSQLType(int.class));
        assertEquals("DECIMAL", typesHelper.getSQLType(Long.class));
        assertEquals("TIMESTAMP", typesHelper.getSQLType(Date.class));
        assertEquals("BOOLEAN", typesHelper.getSQLType(boolean.class));
        assertEquals("BYTEA", typesHelper.getSQLType(byte.class));
    }

    @Test
    public void testGetPrimeryKeySQLType_ShouldReturnSerial() {
        assertEquals("SERIAL", typesHelper.getPrimeryKeySQLType());
    }

    @Test
    public void testSimpleValueConvert_BidirectionalFlow() {
        // Конвертация в строку и обратно для Long
        String longStr = typesHelper.simpleTypeValueToString(12345L);
        assertEquals("12345", longStr);
        assertEquals(12345L, typesHelper.simpleTypeValueFromString(Long.class, longStr));

        // Конвертация для BigDecimal
        BigDecimal decimal = new BigDecimal("99.99");
        String decimalStr = typesHelper.simpleTypeValueToString(decimal);
        assertEquals("99.99", decimalStr);
        assertEquals(decimal, typesHelper.simpleTypeValueFromString(BigDecimal.class, decimalStr));

        // Конвертация для Date (Unix Timestamp)
        Date now = new Date(1711833600000L);
        String dateStr = typesHelper.simpleTypeValueToString(now);
        assertEquals("1711833600000", dateStr);
        assertEquals(now, typesHelper.simpleTypeValueFromString(Date.class, dateStr));
    }

    @Test
    public void testBuildSqlCastFunction_HappyPath() {
        String castString = typesHelper.buildSqlCastFunction("tbl.age", Integer.class);
        // Метод возвращает строковое представление CAST(поле AS ТИП)
        assertNotNull(castString);
        assertTrue(castString.contains("tbl.age"));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testIsSimpleType_WithMockField_ShouldDetectCorrectly() {
        XdStorageObjectField mockField = mock(XdStorageObjectField.class);
        XdStorageObjectFieldInfo mockFieldInfo = mock(XdStorageObjectFieldInfo.class);

        when(mockField.getFieldInfo()).thenReturn(mockFieldInfo);
        when(mockFieldInfo.getValueClass()).thenReturn((Class) String.class);

        // Поля типа String обязаны распознаваться как простые flat-типы СУБД
        assertTrue(typesHelper.isSimpleType(mockField));
    }

    @Test
    public void testSimpleTypeValueToString_WithNull_ShouldThrowNullPointerException() {
        // Граничное условие: Передача null в метод определения типа значения
        assertThrows(NullPointerException.class, () -> {
            typesHelper.simpleTypeValueToString(null);
        });
    }
}
