package org.flib.xdstorage.sqlstorage.configuration;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки граничных условий класса XdStorageSQLConfigurationLoader.
 */
public class XdStorageSQLConfigurationLoaderTest {

    // === ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testLoad_WithNonExistentFile_ShouldThrowXdStorageIOException() {
        // Граничное условие: Попытка загрузить конфигурационный файл, которого нет в ресурсах ClassLoader.
        // Метод должен перехватить NullPointerException потока внутри блока catch (Throwable cause)
        // и гарантированно обернуть его в системное исключение XdStorageIOException СУБД.
        assertThrows(XdStorageIOException.class, () -> {
            XdStorageSQLConfigurationLoader.load("ghost_config_file_123.xml");
        });
    }

    @Test
    public void testLoad_WithNullFilename_ShouldThrowXdStorageIOException() {
        // Граничное условие: Передача null вместо валидного имени файла
        assertThrows(XdStorageIOException.class, () -> {
            XdStorageSQLConfigurationLoader.load(null);
        });
    }
}
