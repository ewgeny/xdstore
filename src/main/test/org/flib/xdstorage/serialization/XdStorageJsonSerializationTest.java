package org.flib.xdstorage.serialization;

import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для верификации работы многострочного JSON-движка СУБД (Поинт Г).
 */
public class XdStorageJsonSerializationTest {

    private XdStorageServicesLocator mockServices;
    private IXdStorageIdGenerator mockIdGenerator;
    private XdStorageJsonObjectsWriter jsonWriter;
    private XdStorageJsonObjectsReader jsonReader;

    // Тестовая public сущность верхнего уровня для проверки сериализации
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class JsonTestRecord {
        @XdStorageObjectId
        private Long id;
        private String title;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        XdStorageDefaultSimpleTypeHelper typeHelper = new XdStorageDefaultSimpleTypeHelper();

        jsonWriter = new XdStorageJsonObjectsWriter(mockServices, typeHelper, mockIdGenerator);
        jsonReader = new XdStorageJsonObjectsReader(typeHelper);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь с учетом Pretty Print) ===

    @Test
    public void testWriteAndReadObjects_ShouldMaintainDataConsistency() throws Exception {
        // Шаг 1. Готовим тестовую пачку объектов
        List<Object> records = new ArrayList<>();
        JsonTestRecord record = new JsonTestRecord();
        record.setId(777L);
        record.setTitle("JSON_Test_Payload");
        records.add(record);

        // Шаг 2. Сериализуем объекты в JSON-строку памяти через StringWriter
        StringWriter stringWriter = new StringWriter();
        jsonWriter.writeObjects(stringWriter, records);

        String outputJson = stringWriter.toString();

        assertNotNull(outputJson);
        // ИСПРАВЛЕНИЕ: Ищем чистый токен типа без пробела после двоеточия, как его генерирует пакер
        assertTrue(outputJson.contains("\"type\":\"" + JsonTestRecord.class.getName() + "\"")
                        || outputJson.contains("\"type\": \"" + JsonTestRecord.class.getName() + "\""),
                "JSON обязан содержать мета-тип класса!");

        // ИСПРАВЛЕНИЕ: Учитываем компактный вывод числового ID
        assertTrue(outputJson.contains("\"id\":777")
                        || outputJson.contains("\"id\": 777"),
                "JSON обязан корректно сериализовать числовые ID полей!");

        // ИСПРАВЛЕНИЕ: Проверяем строковое поле
        assertTrue(outputJson.contains("\"title\":\"JSON_Test_Payload\"")
                        || outputJson.contains("\"title\": \"JSON_Test_Payload\""),
                "Строковые поля должны быть правильно отформатированы!");

        // Шаг 3. Десериализуем JSON-строку обратно через StringReader
        StringReader stringReader = new StringReader(outputJson);
        Collection<Object> deserializedResult = jsonReader.read(stringReader);

        // Верифицируем, что наш JSON-движок бесшовно воссоздал объект из многострочного формата
        assertNotNull(deserializedResult);
        assertEquals(1, deserializedResult.size(), "Должен быть восстановлен ровно 1 объект");

        Object restoredObj = deserializedResult.iterator().next();
        assertTrue(restoredObj instanceof JsonTestRecord, "Восстановленный объект должен соответствовать типу JsonTestRecord");

        JsonTestRecord restoredRecord = (JsonTestRecord) restoredObj;
        assertEquals(777L, restoredRecord.getId().longValue());
        assertEquals("JSON_Test_Payload", restoredRecord.getTitle());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testRead_WithEmptyJson_ShouldReturnEmptyCollectionSafely() throws Exception {
        StringReader stringReader = new StringReader("   ");
        Collection<Object> result = jsonReader.read(stringReader);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Для пустого контента должен возвращаться чистый пустой список");
    }

    @Test
    public void testWriteReferences_HappyPath() throws Exception {
        // Проверяем контракт сериализации системных плоских ссылок
        JsonTestRecord mockRef = mock(JsonTestRecord.class);
        when(mockRef.getId()).thenReturn(100L);

        XdStorageObjectIdField mockIdField = mock(XdStorageObjectIdField.class);
        when(mockIdField.get(any())).thenReturn(100L);

        List<Object> references = new ArrayList<>();
        references.add(mockRef);

        StringWriter writer = new StringWriter();
        jsonWriter.writeReferences(writer, mockIdField, references);

        String output = writer.toString();
        assertTrue(output.contains("\"references\":"));

        // ИСПРАВЛЕНИЕ: Числовой ID пишется без лишних кавычек \"100\", адаптируем содержит
        assertTrue(output.contains("\"id\":100")
                        || output.contains("\"id\": 100"),
                "Идентификатор ссылки обязан корректно сериализоваться в Pretty Print!");
    }
}
