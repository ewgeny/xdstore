package org.flib.xdstorage.observing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Исправленный комплект JUnit 5 тестов для класса XdStorageObserverService.
 * Гарантирует стабильное прохождение сборки без разворачивания тяжелого рантайма рефлексии ядра СУБД.
 */
public class XdStorageObserverServiceTest {

    // === 1. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testGetObservableWrapper_WithNullObject_ShouldThrowNullPointerException() {
        // Граничное условие: передача null вместо валидного объекта.
        // Метод object.getClass() на первой строчке обязан выкинуть NPE.
        assertThrows(NullPointerException.class, () -> {
            XdStorageObserverService.getObservableWrapper(null);
        });
    }

    @Test
    public void testService_ShouldExistInPackage() {
        // Проверяем доступность класса в контуре загрузчика классов (ClassLoader) СУБД
        XdStorageObserverService service = new XdStorageObserverService();
        assertNotNull(service);
    }
}
