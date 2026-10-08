package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Интеграционный тест: Проверка транзакционного маршаллера XdStorageYamlObjectsWriter")
public class XdStorageYamlObjectsWriterTest {

    private XdStorageServicesLocator mockLocator;
    private IXdStorageSimpleTypeHelper simpleTypeHelper;
    private IXdStorageIdGenerator mockIdGenerator;
    private XdStorageYamlObjectsWriter yamlWriter;

    @BeforeEach
    public void setUp() {
        mockLocator = mock(XdStorageServicesLocator.class);
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        yamlWriter = new XdStorageYamlObjectsWriter(mockLocator, simpleTypeHelper, mockIdGenerator);
    }

    @Test
    @DisplayName("Маршаллинг графа: проверка структуры YAML, табуляций и одинарных кавычек метамодели")
    public void testWriteObjects_ValidGraph_ShouldGenerateStrictYamlWithTabulations() throws Exception {
        StringWriter stringWriter = new StringWriter();

        XdStarSystem system = new XdStarSystem();
        system.setId("system-001");
        system.setNewName("Sol");

        XdPlanet planet = new XdPlanet();
        planet.setId(800L);
        planet.setName("Earth");
        planet.setWaterPercent(2);

        system.addPlanet(planet);

        Collection<Object> objectsToWrite = new ArrayList<>();
        objectsToWrite.add(system);

        yamlWriter.writeObjects(stringWriter, objectsToWrite);
        String resultYaml = stringWriter.toString();

        assertNotNull(resultYaml);

        // Верифицируем корневые маркеры блоков
        assertTrue(resultYaml.contains("objects:"));
        assertTrue(resultYaml.contains("- object:"));
        assertTrue(resultYaml.contains("class: 'org.flib.xdstorage.entities.XdStarSystem'"));

        // =========================================================================
        // ПРАВКА АССЕРТOВ ПОД МЕТАМOДЕЛЬ: Проверяем строгую иерархию полей
        // =========================================================================
        assertTrue(resultYaml.contains("fields:"));
        assertTrue(resultYaml.contains("- field:"));
        assertTrue(resultYaml.contains("name: 'id'"));
        assertTrue(resultYaml.contains("type: 'java.lang.String'"));
        assertTrue(resultYaml.contains("value: 'system-001'"));

        assertTrue(resultYaml.contains("name: 'newName'"));
        assertTrue(resultYaml.contains("type: 'java.lang.String'"));
        assertTrue(resultYaml.contains("value: 'Sol'"));

        // Проверяем канонический пропуск null-полей (Вариант 2)
        assertFalse(resultYaml.contains("name: 'satellite'"), "🚨 Ошибка: Поле со значением null попало в файл!");

        // Проверяем геометрию табуляций внутри структуры метамодели полей
        assertTrue(resultYaml.contains("\t\t\t- field:"), "🚨 Нарушена геометрия табуляционных отступов полей!");
    }
}
