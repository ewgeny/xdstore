package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Юнит-тесты: Декомпозированный маршаллер объектов YAML")
public class XdStorageYamlBlockObjectsWriterTest {

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
    @DisplayName("Экранирование кавычек: Одинарная кавычка обязана удваиваться при записи на диск")
    public void testEncode_SingleQuote_ShouldBeDuplicated() {
        String dirtyValue = "Planet 'Omega'";
        String cleanValue = XdStorageYamlBlockObjectsWriter.encode(dirtyValue);
        assertEquals("Planet ''Omega''", cleanValue, "Нарушен канонический инвариант YAML-экранирования!");
    }

    @Test
    @DisplayName("Маршаллинг JavaBeans объектов: Проверка обхода свойств сущности")
    public void testWriteObjectData_ValidEntity_ShouldWriteMetadataAndClass() throws Exception {
        StringWriter writer = new StringWriter();
        XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

        XdPlanet planet = new XdPlanet();
        planet.setId(500L);
        planet.setName("Tatooine");

        XdStorageYamlBlockObjectsWriter.writeObjectData(planet, emitter, mockLocator, simpleTypeHelper, mockIdGenerator);

        String result = writer.toString();
        assertTrue(result.contains("class: 'org.flib.xdstorage.entities.XdPlanet'"));
        assertTrue(result.contains("id: '500'"));
        assertTrue(result.contains("name: 'Tatooine'"));
    }
}
