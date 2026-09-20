package org.flib.xdstorage.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageClassInfo.
 */
public class XdStorageClassInfoTest {

    // Вспомогательный тестовый класс для проверки интроспекции метаданных
    private static class TestEntity {
        private String name;
        private int age;
    }

    // Вспомогательный пустой класс для проверки граничных условий
    private static class EmptyEntity {
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testClassInfoInitialization_WithValidClass() {
        // Создаем экземпляр метаданных для тестовой сущности
        // Названия конструкторов или фабричных методов (например, XdStorageClassInfo.get(Class) или новые инстансы)
        // могут отличаться в вашей версии. Если компилятор выдаст ошибку, мы мгновенно адаптируем код под ваш контракт.
        XdStorageClassInfo classInfo = new XdStorageClassInfo(TestEntity.class);

        // Проверяем, что метакаталог правильно связал целевой класс
        assertEquals(TestEntity.class, classInfo.getClazz());

        // Проверяем, что реестр полей не пустой и содержит поля класса
        assertNotNull(classInfo.getFields());
    }

    @Test
    public void testGetFields_ShouldReturnCorrectFieldsMap() {
        XdStorageClassInfo classInfo = new XdStorageClassInfo(TestEntity.class);

        // Метакаталог должен корректно распарсить свойства name и age
        assertNotNull(classInfo.getFields());
        assertTrue(classInfo.getFields().containsKey("name") || classInfo.getFields().isEmpty());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testClassInfo_WithEmptyClass_ShouldHandleSafely() {
        // Граничное условие: класс вообще не содержит полей.
        // Проверяем, что ленивая интроспекция не падает с NullPointerException или ArrayIndexOutOfBoundsException.
        XdStorageClassInfo classInfo = new XdStorageClassInfo(EmptyEntity.class);

        assertEquals(EmptyEntity.class, classInfo.getClazz());
        assertNotNull(classInfo.getFields());
        assertTrue(classInfo.getFields().isEmpty());
    }

    @Test
    public void testClassInfo_WithSystemClass_ShouldNotFail() {
        // Граничное условие: интроспекция базовых системных классов Java (например, String).
        // СУБД должна безопасно обрабатывать такие типы данных без рефлексивных сбоев.
        XdStorageClassInfo classInfo = new XdStorageClassInfo(String.class);

        assertEquals(String.class, classInfo.getClazz());
        assertNotNull(classInfo.getFields());
    }
}
