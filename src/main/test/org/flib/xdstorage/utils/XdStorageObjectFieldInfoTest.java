package org.flib.xdstorage.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий реального класса XdStorageObjectFieldInfo.
 */
public class XdStorageObjectFieldInfoTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testFieldInfo_WithSimpleStringField_ShouldStoreCorrectFlags() {
        // Обычная строка: не массив, не коллекция, не мапа, ключа нет, значение String
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                String.class, false, false, false, null, String.class
        );

        assertEquals(String.class, fieldInfo.getClazz());
        assertFalse(fieldInfo.isArray());
        assertFalse(fieldInfo.isCollection());
        assertFalse(fieldInfo.isMap());
        assertNull(fieldInfo.getMapKeyClass());
        assertEquals(String.class, fieldInfo.getValueClass());
    }

    @Test
    public void testFieldInfo_WithGenericCollection_ShouldDetectCollectionFlag() {
        // Список списков или просто List<Integer>: коллекция, значение Integer.class
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                List.class, false, true, false, null, Integer.class
        );

        assertEquals(List.class, fieldInfo.getClazz());
        assertFalse(fieldInfo.isArray());
        assertTrue(fieldInfo.isCollection());
        assertFalse(fieldInfo.isMap());
        assertNull(fieldInfo.getMapKeyClass());
        assertEquals(Integer.class, fieldInfo.getValueClass());
    }

    @Test
    public void testFieldInfo_WithStandardMap_ShouldStoreKeyAndValueClasses() {
        // Ассоциативный массив Map<String, Long>: мапа, ключ String, значение Long
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                Map.class, false, false, true, String.class, Long.class
        );

        assertEquals(Map.class, fieldInfo.getClazz());
        assertFalse(fieldInfo.isArray());
        assertFalse(fieldInfo.isCollection());
        assertTrue(fieldInfo.isMap());
        assertEquals(String.class, fieldInfo.getMapKeyClass());
        assertEquals(Long.class, fieldInfo.getValueClass());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testFieldInfo_WithPrimitiveArray_ShouldHandleArrayFlags() {
        // Граничное условие: массив примитивов (например, byte[])
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                byte[].class, true, false, false, null, byte.class
        );

        assertEquals(byte[].class, fieldInfo.getClazz());
        assertTrue(fieldInfo.isArray());
        assertFalse(fieldInfo.isCollection());
        assertFalse(fieldInfo.isMap());
        assertNull(fieldInfo.getMapKeyClass());
        assertEquals(byte.class, fieldInfo.getValueClass());
    }

    @Test
    public void testFieldInfo_WithAllNullClassesAndFalseFlags_ShouldNotFail() {
        // Граничное условие: экстремальная передача null во все объектные типы
        // Проверяем, что Plain Old Java Object контейнер устойчив к отсутствию метатипов
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                null, false, false, false, null, null
        );

        assertNull(fieldInfo.getClazz());
        assertFalse(fieldInfo.isArray());
        assertFalse(fieldInfo.isCollection());
        assertFalse(fieldInfo.isMap());
        assertNull(fieldInfo.getMapKeyClass());
        assertNull(fieldInfo.getValueClass());
    }

    @Test
    public void testFieldInfo_WithConflictingFlags_ShouldStoreExactlyWhatPassed() {
        // Граничное условие: проверка иммутабельности данных при передаче
        // противоречивых флагов (одновременно массив, коллекция и мапа).
        // Так как класс является чисто DTO-контейнером метаданных, он должен строго
        // сохранить то, что передано, не пытаясь упасть с внутренней валидацией.
        XdStorageObjectFieldInfo fieldInfo = new XdStorageObjectFieldInfo(
                Object.class, true, true, true, Object.class, Object.class
        );

        assertTrue(fieldInfo.isArray());
        assertTrue(fieldInfo.isCollection());
        assertTrue(fieldInfo.isMap());
    }
}
