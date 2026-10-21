package io.github.xdstorage.search.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XdStorageSearchKeyIndexRecord: запись key-дерева (ид объекта -> первичное значение)")
public class XdStorageSearchKeyIndexRecordTest {

    @Test
    @DisplayName("Пустой конструктор: поля null, сеттеры работают")
    public void testEmptyConstructorAndSetters() {
        final XdStorageSearchKeyIndexRecord record = new XdStorageSearchKeyIndexRecord();

        assertNull(record.getObjectId());
        assertNull(record.getValue());

        record.setObjectId(7L);
        record.setValue("alpha");

        assertEquals(7L, record.getObjectId());
        assertEquals("alpha", record.getValue());
    }

    @Test
    @DisplayName("Полный конструктор: ид и значение доступны через геттеры")
    public void testFullConstructor() {
        final XdStorageSearchKeyIndexRecord record = new XdStorageSearchKeyIndexRecord(42L, "beta");

        assertEquals(42L, record.getObjectId());
        assertEquals("beta", record.getValue());
    }

    @Test
    @DisplayName("Допускает null-значение первичного поля")
    public void testNullValueAllowed() {
        final XdStorageSearchKeyIndexRecord record = new XdStorageSearchKeyIndexRecord(1L, null);

        assertEquals(1L, record.getObjectId());
        assertNull(record.getValue());
    }
}
