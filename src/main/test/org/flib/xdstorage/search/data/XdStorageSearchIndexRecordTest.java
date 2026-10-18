package org.flib.xdstorage.search.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XdStorageSearchIndexRecord: запись поискового индекса")
public class XdStorageSearchIndexRecordTest {

    @Test
    @DisplayName("Новая запись: все поля null, дочерние коллекции пустые")
    public void testFreshRecord() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        assertNull(record.getId());
        assertNull(record.getResourceId());
        assertNull(record.getPrimaryIndexValue());
        assertNull(record.getFieldValue("any"));
        assertNull(record.getFieldValues());
        assertNull(record.getChildrenFieldValues());
        assertTrue(record.getChildFieldValue("children", String.class, "any").isEmpty());
    }

    @Test
    @DisplayName("Идентификаторы: id и resourceId")
    public void testIdentifiers() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.setId(7L);
        record.setResourceId("res-7");

        assertEquals(7L, record.getId());
        assertEquals("res-7", record.getResourceId());
    }

    @Test
    @DisplayName("Простые поля: setFieldValue/getFieldValue и пакетная установка")
    public void testFieldValues() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.setFieldValue("name", "alpha");
        record.setFieldValue("tonnage", 100);

        assertEquals("alpha", record.getFieldValue("name"));
        assertEquals(100, record.getFieldValue("tonnage"));
        assertNotNull(record.getFieldValues());
        assertEquals(2, record.getFieldValues().size());

        final Map<String, Object> batch = new HashMap<>();
        batch.put("code", "X1");
        record.setFieldValues(batch);
        assertEquals("X1", record.getFieldValue("code"));
        assertSame(batch, record.getFieldValues());
    }

    @Test
    @DisplayName("addChildFieldValue добавляет значение и автоматически регистрирует ид потомка")
    public void testAddChildFieldValueRegistersChildId() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");

        final Collection<Object> ranks = record.getChildFieldValue("crew", String.class, "rank");
        assertEquals(1, ranks.size());
        assertEquals("Pilot", ranks.iterator().next());

        final Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> children = record.getChildrenFieldValues();
        assertEquals("Pilot", children.get(String.class).get(1L).get("crew").get("rank"));
    }

    @Test
    @DisplayName("addChildFieldValue: повторная запись того же значения не дублируется")
    public void testAddChildFieldValueIsIdempotentForSameValue() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");

        final Map<String, Map<String, Object>> byField = record.getChildrenFieldValues().get(String.class).get(1L);
        assertEquals(1, byField.get("crew").size());
        assertEquals("Pilot", byField.get("crew").get("rank"));
    }

    @Test
    @DisplayName("addChildFieldValue: новое значение замещает прежнее")
    public void testAddChildFieldValueReplacesChangedValue() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Captain");

        assertEquals("Captain", record.getChildrenFieldValues().get(String.class).get(1L).get("crew").get("rank"));
    }

    @Test
    @DisplayName("addChildFieldValue: null и не-null значения различаются корректно")
    public void testAddChildFieldValueNullSemantics() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.addChildFieldValue("crew", String.class, 1L, "rank", null);
        // null + null: идемпотентно
        record.addChildFieldValue("crew", String.class, 1L, "rank", null);
        assertNull(record.getChildrenFieldValues().get(String.class).get(1L).get("crew").get("rank"));

        // null -> значение
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");
        assertEquals("Pilot", record.getChildrenFieldValues().get(String.class).get(1L).get("crew").get("rank"));
    }

    @Test
    @DisplayName("addChildIdIfNotExists: ид потомков уникальны, группируются по классу и полю")
    public void testAddChildIdIfNotExists() throws Exception {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.addChildIdIfNotExists("crew", String.class, 1L);
        record.addChildIdIfNotExists("crew", String.class, 1L); // дубликат не добавляется
        record.addChildIdIfNotExists("crew", String.class, 2L);
        record.addChildIdIfNotExists("captain", String.class, 3L);

        // childrenIds не имеет публичного геттера - проверяем контракт через рефлексию
        final java.lang.reflect.Field idsField = XdStorageSearchIndexRecord.class.getDeclaredField("childrenIds");
        idsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Map<Class<?>, Map<String, List<Object>>> ids =
                (Map<Class<?>, Map<String, List<Object>>>) idsField.get(record);

        assertEquals(Arrays.asList(1L, 2L), ids.get(String.class).get("crew"));
        assertEquals(Collections.singletonList(3L), ids.get(String.class).get("captain"));
    }

    @Test
    @DisplayName("getChildFieldValue без учета регистра имени поля")
    public void testGetChildFieldValueCaseInsensitive() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");

        final Collection<Object> byUpper = record.getChildFieldValue("crew", String.class, "RANK");
        assertEquals(1, byUpper.size());
        assertEquals("Pilot", byUpper.iterator().next());
    }

    @Test
    @DisplayName("getChildFieldValue агрегирует значения по всем объектам класса")
    public void testGetChildFieldValueAggregatesAcrossObjects() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");
        record.addChildFieldValue("crew", String.class, 2L, "rank", "Doctor");

        final Collection<Object> ranks = record.getChildFieldValue("crew", String.class, "rank");
        assertEquals(2, ranks.size());
        assertTrue(ranks.contains("Pilot"));
        assertTrue(ranks.contains("Doctor"));
    }

    @Test
    @DisplayName("getChildFieldValue: неизвестный класс или поле - пустая коллекция")
    public void testGetChildFieldValueMissingClassOrField() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();
        record.addChildFieldValue("crew", String.class, 1L, "rank", "Pilot");

        assertTrue(record.getChildFieldValue("crew", Integer.class, "rank").isEmpty());
        assertTrue(record.getChildFieldValue("crew", String.class, "unknown").isEmpty());
        // дочернее поле зарегистрировано, но имя поля не совпадает ни у одного потомка
        assertTrue(record.getChildFieldValue("unknownField", String.class, "rank").isEmpty());
    }

    @Test
    @DisplayName("Сеттер дочерних значений: прямая установка карты")
    public void testSetChildrenFieldValues() {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        final Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> children = new HashMap<>();
        record.setChildrenFieldValues(children);

        assertSame(children, record.getChildrenFieldValues());
    }
}
