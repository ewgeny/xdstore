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

@DisplayName("Юнит-тесты: Декомпозированный маршаллер контейнеров YAML")
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
    @DisplayName("Маршаллинг коллекций: Сериализация списков со смешанными примитивами и null ячейками")
    public void testWriteCollection_MixedElements_ShouldFormatStrictList() throws Exception {
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
        assertTrue(result.contains("collection:"));
        assertTrue(result.contains("- 'Mars'"));
        assertTrue(result.contains("- null"), "Ячейка null в массиве обязана сохранить свой явный строковый маркер!");
    }

    @Test
    @DisplayName("Маршаллинг мап: Проверка сериализации плоских HashMap объектов")
    public void testWriteMap_StandardHashMap_ShouldGenerateEntries() throws Exception {
        StringWriter writer = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

        Map<String, String> map = new HashMap<>();
        map.put("sector", "Sector-7G");

        XdStorageYamlBlockContainersWriter.writeMap(
                "metadata", HashMap.class, map, emitter, mockLocator, simpleTypeHelper, mockIdGenerator
        );

        String result = writer.toString();
        assertTrue(result.contains("metadata:"));
        assertTrue(result.contains("map:"));
        assertTrue(result.contains("- entry:"));

        // =========================================================================
        // СИНТАКСИЧЕСКИЙ ФИКС ТЕСТА: Проверяем новые плоские, чистые пары ключ-значение
        // без паразитных дефисов и многострочных переносов рассинхронизации!
        // =========================================================================
        assertTrue(result.contains("key: 'sector'"));
        assertTrue(result.contains("value: 'Sector-7G'"));
    }

}
