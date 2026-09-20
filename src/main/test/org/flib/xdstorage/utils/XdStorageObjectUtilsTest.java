package org.flib.xdstorage.utils;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Полный комплект JUnit 5 тестов для проверки базовых и граничных условий класса XdStorageObjectUtils.
 * Полностью адаптирован под архитектуру и инварианты безопасности СУБД xdstorage-2.0.
 */
public class XdStorageObjectUtilsTest {

    // Тестовая POJO сущность для проверки глубокого копирования и рефлексивного заполнения
    public static class SampleEntity {
        private String name;
        private int value;
        private List<String> tags = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь / Happy Path) ===

    @Test
    public void testCloneObject_WithValidPojo_ShouldCreateDeepCopy() {
        SampleEntity original = new SampleEntity();
        original.setName("OriginalName");
        original.setValue(100);
        original.getTags().add("v2-core");

        // Проверяем глубокое клонирование объекта через рефлексивный движок СУБД
        SampleEntity cloned = XdStorageObjectUtils.cloneObject(original);

        assertNotNull(cloned);
        assertNotSame(original, cloned); // Гарантируем разные ссылки в куче (Heap)
        assertEquals(original.getName(), cloned.getName());
        assertEquals(original.getValue(), cloned.getValue());
        assertEquals(original.getTags(), cloned.getTags());
    }

    @Test
    public void testFillObject_WithValidSource_ShouldCopyFieldsCorrectly() {
        SampleEntity source = new SampleEntity();
        source.setName("SourceData");
        source.setValue(500);

        SampleEntity target = new SampleEntity();

        // Заполняем целевой объект данными из источника через интроспекцию свойств
        XdStorageObjectUtils.fillObject(target, source);

        assertEquals(source.getName(), target.getName());
        assertEquals(source.getValue(), target.getValue());
    }

    @Test
    public void testIsSimpleReference_WithStandardObjects_ShouldReturnFalse() {
        // Обычный базовый Object не является сущностью СУБД, метод обязан вернуть false
        assertFalse(XdStorageObjectUtils.isSimpleReference(new Object()));

        // Строка является плоским типом значения, а не ссылкой на другую таблицу — проверяем безопасный возврат
        Object result = XdStorageObjectUtils.getWrappedObjectOrSameObject("string_check");
        assertNotNull(result);
        assertEquals("string_check", result);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ (Edge Cases) ===

    @Test
    public void testCloneObject_WithNullInput_ShouldReturnNullSafely() {
        // Граничное условие: передача null на вход метода клонирования (Защита на верхнем уровне)
        assertNull(XdStorageObjectUtils.cloneObject(null));
    }

    @Test
    public void testFillObject_WithNullArguments_ShouldThrowNullPointerException() {
        // Граничное условие: передача null в качестве источника или цели заполнения свойств.
        // Тесты зафиксировали, что внутренний рантайм СУБД не делает проверок и падает с NPE.
        SampleEntity valid = new SampleEntity();

        assertThrows(NullPointerException.class, () -> XdStorageObjectUtils.fillObject(null, valid));
        assertThrows(NullPointerException.class, () -> XdStorageObjectUtils.fillObject(valid, null));
        assertThrows(NullPointerException.class, () -> XdStorageObjectUtils.fillObject(null, null));
    }

    @Test
    public void testCloneObject_WithStandardDateType_ShouldDeepCopyDate() {
        // Граничное условие: изолированная проверка клонирования системных типов данных (java.util.Date)
        Date originalDate = new Date();

        Date clonedDate = XdStorageObjectUtils.cloneObject(originalDate);

        assertNotNull(clonedDate);
        assertNotSame(originalDate, clonedDate);
        assertEquals(originalDate.getTime(), clonedDate.getTime());
    }

    @Test
    public void testGetWrappedObjectOrSameObject_WithNull_ShouldThrowNullPointerException() {
        // Граничное условие: разворачивание прокси-цепочек для null-ссылок.
        // Архитектура ожидает валидный объект, поэтому передача null приводит к предсказуемому NPE.
        assertThrows(NullPointerException.class, () -> XdStorageObjectUtils.getWrappedObjectOrSameObject(null));
    }
}
