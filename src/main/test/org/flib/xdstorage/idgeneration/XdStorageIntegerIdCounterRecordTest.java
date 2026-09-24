package org.flib.xdstorage.idgeneration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки JavaBean инвариантов класса XdStorageIntegerIdCounterRecord.
 */
public class XdStorageIntegerIdCounterRecordTest {

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageIntegerIdCounterRecord record = new XdStorageIntegerIdCounterRecord();

        record.setCl(String.class);
        record.setCounter(500);

        assertEquals(String.class, record.getCl());
        assertEquals(500, record.getCounter());
    }

    @Test
    public void testRecord_WithNullFields_ShouldHandleSafely() {
        XdStorageIntegerIdCounterRecord record = new XdStorageIntegerIdCounterRecord();

        // Граничное условие: Дефолтное состояние JavaBean должно быть пустым без NPE
        assertNull(record.getCl());
        assertNull(record.getCounter());
    }
}
