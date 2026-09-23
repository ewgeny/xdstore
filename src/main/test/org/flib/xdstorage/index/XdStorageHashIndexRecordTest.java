package org.flib.xdstorage.index.hash;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки JavaBean контрактов класса XdStorageHashIndexRecord.
 */
public class XdStorageHashIndexRecordTest {

    @Test
    public void testConstructorAndGetters_HappyPath() {
        // Проверяем конструктор с аргументами и геттеры
        XdStorageHashIndexRecord record = new XdStorageHashIndexRecord("object_uuid", "file_partition_01");

        assertEquals("object_uuid", record.getObjectId());
        assertEquals("file_partition_01", record.getResourceId());
    }

    @Test
    public void testSetters_HappyPath() {
        XdStorageHashIndexRecord record = new XdStorageHashIndexRecord();

        record.setObjectId("custom_obj");
        record.setResourceId("custom_res");

        assertEquals("custom_obj", record.getObjectId());
        assertEquals("custom_res", record.getResourceId());
    }

    @Test
    public void testRecord_WithNullFields_ShouldHandleSafely() {
        // Граничное условие: Дефолтный пустой конструктор не должен приводить к исключениям
        XdStorageHashIndexRecord record = new XdStorageHashIndexRecord();

        assertNull(record.getObjectId());
        assertNull(record.getResourceId());
    }
}
