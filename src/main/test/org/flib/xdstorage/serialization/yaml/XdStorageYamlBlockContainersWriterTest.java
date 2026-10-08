package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Юнит-тесты: Тотально типизированный маршаллер контейнеров YAML (Коллекции и Мапы)")
public class XdStorageYamlBlockContainersWriterTest {

    private XdStorageServicesLocator mockLocator;
    private IXdStorageSimpleTypeHelper simpleTypeHelper;
    private IXdStorageIdGenerator mockIdGenerator;

    @BeforeEach
    public void setUp() {
        mockLocator = mock(XdStorageServicesLocator.class);
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
        mockIdGenerator = mock(IXdStorageIdGenerator.class);
    }

    @Test
    @DisplayName("Кейс 1: Маршаллинг коллекций — Сериализация списков в структуру - item: type/value с null ячейками")
    public void testWriteCollection_MixedElements_ShouldFormatStrictItemsList() throws Exception {
        StringWriter writer = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

        List<Object> list = new ArrayList<>();
        list.add("Mars");
        list.add(null);

        XdStorageYamlBlockContainersWriter.writeCollection(
                "planets", ArrayList.class, list, emitter, mockLocator, simpleTypeHelper, mockIdGenerator
        );

        String result = writer.toString();

        assertTrue(result.contains("planets:"));
        assertTrue(result.contains("class: 'java.util.ArrayList'"));

        // Верифицируем строгие объектные ячейки списка
        assertTrue(result.contains("- item:"));
        assertTrue(result.contains("type: 'java.lang.String'"));
        assertTrue(result.contains("value: 'Mars'"));

        // Верифицируем каноничный полиморфный маркер для null-элемента
        assertTrue(result.contains("type: 'null'"));
        assertTrue(result.contains("value: null"));
    }

    @Test
    @DisplayName("Кейс 2: Маршаллинг мап — Проверка генерации чистых структур entry -> key/value -> type/value")
    public void testWriteMap_StandardHashMap_ShouldGenerateStrictObjects() throws Exception {
        StringWriter writer = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

        Map<String, String> map = new HashMap<>();
        map.put("sector", "Sector-7G");

        XdStorageYamlBlockContainersWriter.writeMap(
                "metadata", HashMap.class, map, emitter, mockLocator, simpleTypeHelper, mockIdGenerator
        );

        String result = writer.toString();

        assertTrue(result.contains("metadata:"));
        assertTrue(result.contains("class: 'java.util.HashMap'"));
        assertTrue(result.contains("- entry:"));

        // Проверяем, что узел key имеет строгое разделение на тип и значение
        assertTrue(result.contains("key:"));
        assertTrue(result.contains("type: 'java.lang.String'"));
        assertTrue(result.contains("value: 'sector'"));

        // Проверяем то же самое для узла value
        assertTrue(result.contains("value:"));
        assertTrue(result.contains("type: 'java.lang.String'"));
        assertTrue(result.contains("value: 'Sector-7G'"));
    }
}
