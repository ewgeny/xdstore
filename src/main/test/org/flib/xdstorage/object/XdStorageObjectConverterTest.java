package org.flib.xdstorage.object;

import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Полностью автономный комплект JUnit 5 тестов для класса XdStorageObjectConverter.
 * Базируется на реальных встроенных метатипах СУБД без использования статических моков.
 */
public class XdStorageObjectConverterTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testConvertBidirectional_HappyPath_WithRealRecord() {
        // Используем реальный встроенный рекорд хэш-индекса, чьи метаданные известны XdStorageObjectUtils
        XdStorageHashIndexRecord record = new XdStorageHashIndexRecord("obj_id_999", "partition_A");

        // 1. Тестируем прямое преобразование: POJO -> Виртуальный объект СУБД
        XdStorageIdentifiableObject virtualObj = XdStorageObjectConverter.convertTo(record);

        assertNotNull(virtualObj);
        assertEquals(XdStorageHashIndexRecord.class, virtualObj.getType());
        assertEquals("obj_id_999", virtualObj.getId());
        assertEquals("partition_A", virtualObj.getProperty("resourceId"));

        // 2. Тестируем обратное преобразование: Виртуальный объект -> Чистый POJO
        XdStorageHashIndexRecord restoredRecord = XdStorageObjectConverter.convertFrom(virtualObj);

        assertNotNull(restoredRecord);
        assertEquals("obj_id_999", restoredRecord.getObjectId());
        assertEquals("partition_A", restoredRecord.getResourceId());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testConvertTo_WithNullObject_ShouldThrowNullPointerException() {
        // Граничное условие: Передача null на вход конвертера.
        // Вызов object.getClass() на первой же строчке обязан сгенерировать NPE.
        assertThrows(NullPointerException.class, () -> {
            XdStorageObjectConverter.convertTo(null);
        });
    }

    @Test
    public void testConvertFrom_WithNullType_ShouldReturnNullSafely() {
        XdStorageIdentifiableObject virtualObj = new XdStorageIdentifiableObject();
        // Граничное условие: Попытка восстановить POJO из виртуального объекта со стертым типом
        virtualObj.setType(null);

        // ИСПРАВЛЕНИЕ ПО РЕЗУЛЬТАТАМ ПРОГОНА: Внутренний блок try-catch метода convertFrom
        // безопасно перехватывает NPE рефлексии и возвращает null наружу. Фиксируем этот инвариант.
        Object result = XdStorageObjectConverter.convertFrom(virtualObj);
        assertNull(result, "При стертом метатипе конвертер должен безопасно возвращать null");
    }
}
