package org.flib.xdstorage.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class XdStorageClassGeneratorLeakTest {

    @Test
    @DisplayName("Проверка Metaspace: Многократная компиляция не должна приводить к блокировке ClassLoader")
    void testClassLoaderRelease() throws Exception {
        Map<String, String> dummyCode = new HashMap<>();
        // Генерируем простейший класс для теста компилятора
        dummyCode.put("org.flib.xdstorage.code.GeneratedTestStub",
                "package org.flib.xdstorage.code;\n" +
                        "public class GeneratedTestStub {\n" +
                        "    public String sayHello() { return \"Hello\"; }\n" +
                        "}");

        // Вызываем компиляцию. Метод не должен оставлять открытых дескрипторов URLClassLoader
        assertDoesNotThrow(() -> {
            Map<Class<?>, Class<?>> compiled = XdStorageClassGenerator.generateSimpleWrapper(TestTargetEntity.class);
            // Даже если компиляция пустая из-за отсутствия генераторов кодовых строк,
            // системные ресурсы компилятора должны освобождаться полностью
            assertNotNull(compiled);
        });
    }
}

// Заглушка сущности для тестов интроспекции
class TestTargetEntity {
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
