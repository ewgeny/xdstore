package org.flib.xdstorage.code;

import org.junit.jupiter.api.Test;
import javax.tools.JavaFileObject;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageJavaSourceFromString.
 */
public class XdStorageJavaSourceFromStringTest {

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testSourceCreation_HappyPath_ShouldStoreCodeAndName() {
        String className = "org.flib.xdstorage.code.GeneratedTestClass";
        String sampleCode = "package org.flib.xdstorage.code; public class GeneratedTestClass {}";

        XdStorageJavaSourceFromString source = new XdStorageJavaSourceFromString(className, sampleCode);

        // Проверяем инварианты виртуального Java-файла в памяти
        assertEquals(className, source.getName());
        assertEquals(sampleCode, source.getCharContent(true));
        assertEquals(JavaFileObject.Kind.SOURCE, source.getKind());

        // Проверяем корректность генерации URI для компилятора javac
        URI uri = source.toUri();
        assertNotNull(uri);
        assertTrue(uri.toString().contains("org/flib/xdstorage/code/GeneratedTestClass"));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testSource_WithNullOrEmptyCode_ShouldHandleSafely() {
        // Граничное условие: Передача пустого содержимого (например, при сбое генератора)
        XdStorageJavaSourceFromString source = new XdStorageJavaSourceFromString("EmptyClass", "");

        assertEquals("EmptyClass", source.getName());
        assertEquals("", source.getCharContent(false));
    }
}
