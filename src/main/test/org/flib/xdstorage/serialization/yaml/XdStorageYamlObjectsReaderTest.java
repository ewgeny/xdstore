package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.entities.XdUniverse;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные тесты: Фасад диспетчера XdStorageYamlObjectsReader")
public class XdStorageYamlObjectsReaderTest {

    private IXdStorageSimpleTypeHelper simpleTypeHelper;
    private XdStorageYamlObjectsReader yamlReader;

    @BeforeEach
    public void setUp() {
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
        yamlReader = new XdStorageYamlObjectsReader(simpleTypeHelper);
    }

    @Test
    @DisplayName("Демаршаллинг YAML: проверка сборки объектов, распаковки кавычек и восстановления коллекций")
    public void testReadObjects_ValidYamlStream_ShouldReconstructJavaObjects() throws Exception {
        String inputYaml =
                "objects:\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdStarSystem'\r\n" +
                        "\t\tid: 'system-100'\r\n" +
                        "\t\tnewName: 'Sol-''System'''\r\n" +
                        "\t\tplanets:\r\n" +
                        "\t\t\tcollection:\r\n" +
                        "\t\t\t\tclass: 'java.util.ArrayList'\r\n" +
                        "\t\t\t\t- object:\r\n" +
                        "\t\t\t\t\tclass: 'org.flib.xdstorage.entities.XdPlanet'\r\n" +
                        "\t\t\t\t\tid: '800'\r\n" +
                        "\t\t\t\t\tname: 'Earth'\r\n" +
                        "\t\t\t\t\twaterPercent: 2\r\n" +
                        "\t\t\t\t- null\r\n";

        StringReader stringReader = new StringReader(inputYaml);
        Collection<Object> deserializedObjects = yamlReader.readObjects(stringReader);

        assertNotNull(deserializedObjects);
        assertEquals(1, deserializedObjects.size());

        Object rootObj = deserializedObjects.iterator().next();
        assertTrue(rootObj instanceof XdStarSystem);

        XdStarSystem system = (XdStarSystem) rootObj;
        assertEquals("system-100", system.getId());
        assertEquals("Sol-'System'", system.getNewName());

        Collection<XdPlanet> planets = system.getPlanets();
        assertNotNull(planets);
        assertEquals(2, planets.size());

        List<XdPlanet> planetsList = new ArrayList<>(planets);
        XdPlanet planet = planetsList.get(0);
        assertNotNull(planet);
        assertEquals(Long.valueOf(800L), planet.getId());
        assertEquals("Earth", planet.getName());
        assertEquals(2, planet.getWaterPercent());
        assertNull(planet.getSatellite());
        assertNull(planetsList.get(1));
    }

    @Test
    @DisplayName("Тест плоских референсов: Разбор пакетного файла индексов СУБД (readReferences)")
    public void testReadReferences_BatchIndexFile_ShouldExtractAllPointers() throws Exception {
        // =========================================================================
        // СИНТАКСИЧЕСКИЙ ФИКС ТЕСТА: Полностью убираем дефектный двойной слэш \\r\n
        // на чистый канонический маркер переноса строки \r\n для Linux-сред JVM!
        // =========================================================================
        String batchReferencesYaml =
                "references:\r\n" +
                        "\t- reference:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdUniverse'\r\n" +
                        "\t\tobjectId: 'universe-alpha'\r\n" +
                        "\t- reference:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdUniverse'\r\n" +
                        "\t\tobjectId: 'universe-beta'\r\n";

        StringReader stringReader = new StringReader(batchReferencesYaml);
        XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(XdUniverse.class);
        XdStorageObjectIdField idField = clInfo.getIdField();

        Collection<Object> references = yamlReader.readReferences(stringReader, idField);

        assertNotNull(references, "Метод вернул null!");
        assertEquals(2, references.size(), "Пакетный индекс СУБД вычитан неполностью!");
    }

    @Test
    @DisplayName("Fail-Safe барьер: Попытка чтения битого корневого маркера должна вернуть пустую коллекцию")
    public void testReadObjects_CorruptedRootMarker_ShouldReturnEmptyCollection() throws Exception {
        String corruptedYaml =
                "invalid_root_marker:\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdUniverse'\r\n";

        StringReader stringReader = new StringReader(corruptedYaml);
        Collection<Object> result = yamlReader.readObjects(stringReader);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
