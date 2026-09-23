package org.flib.xdstorage.structure.update;

import org.junit.jupiter.api.Test;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки JavaBean контрактов класса XdStorageUpdateStructureRecord.
 */
public class XdStorageUpdateStructureRecordTest {

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageUpdateStructureRecord record = new XdStorageUpdateStructureRecord();
        Date now = new Date();

        record.setName("migration-v2-add-indexes");
        record.setTime(now);
        record.setResult(XdStorageUpdateStructureResult.SUCCESSFULLY);

        assertEquals("migration-v2-add-indexes", record.getName());
        assertEquals(now, record.getTime());
        assertEquals(XdStorageUpdateStructureResult.SUCCESSFULLY, record.getResult());
    }

    @Test
    public void testRecord_WithNullFields_ShouldHandleSafely() {
        // Граничное условие: Дефолтный пустой контейнер не должен приводить к исключениям
        XdStorageUpdateStructureRecord record = new XdStorageUpdateStructureRecord();

        assertNull(record.getName());
        assertNull(record.getTime());
        assertNull(record.getResult());
    }
}
