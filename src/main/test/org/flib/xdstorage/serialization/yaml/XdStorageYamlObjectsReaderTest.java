package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.entities.XdPlanet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Интеграционные юнит-тесты: Тотально типизированный диспетчер XdStorageYamlObjectsReader")
public class XdStorageYamlObjectsReaderTest {

    private IXdStorageSimpleTypeHelper simpleTypeHelper;
    private XdStorageYamlObjectsReader yamlReader;

    @BeforeEach
    public void setUp() {
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
        yamlReader = new XdStorageYamlObjectsReader(simpleTypeHelper);
    }

    @Test
    @DisplayName("Кейс 1: Демаршаллинг каноничной мета-модели (имя + тип + значение) и распаковка кавычек")
    public void testReadObjects_StrictMetaModel_ShouldReconstructJavaObjects() throws Exception {
        // Формируем эталонный YAML строго по нашей новой тотально типизированной спецификации.
        // Каждое поле — это строго структурированная тройка: name, type, value.
        String inputYaml =
                "objects:\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdStarSystem'\r\n" +
                        "\t\tfields:\r\n" +
                        "\t\t\t- field:\r\n" +
                        "\t\t\t\tname: 'id'\r\n" +
                        "\t\t\t\ttype: 'java.lang.String'\r\n" +
                        "\t\t\t\tvalue: 'system-100'\r\n" +
                        "\t\t\t- field:\r\n" +
                        "\t\t\t\tname: 'newName'\r\n" +
                        "\t\t\t\ttype: 'java.lang.String'\r\n" +
                        "\t\t\t\tvalue: 'Sol-''System'''\r\n" + // Проверяем схлопывание кавычек '' -> '
                        "\t\t\t- field:\r\n" +
                        "\t\t\t\tname: 'planets'\r\n" +
                        "\t\t\t\ttype: 'java.util.Collection'\r\n" +
                        "\t\t\t\tvalue:\r\n" +
                        "\t\t\t\t\tcollection:\r\n" +
                        "\t\t\t\t\t\tclass: 'java.util.ArrayList'\r\n" +
                        "\t\t\t\t\t\t- item:\r\n" +
                        "\t\t\t\t\t\t\ttype: 'org.flib.xdstorage.entities.XdPlanet'\r\n" +
                        "\t\t\t\t\t\t\tvalue:\r\n" +
                        "\t\t\t\t\t\t\t\tobject:\r\n" +
                        "\t\t\t\t\t\t\t\t\tclass: 'org.flib.xdstorage.entities.XdPlanet'\r\n" +
                        "\t\t\t\t\t\t\t\t\tfields:\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t- field:\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tname: 'id'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\ttype: 'java.lang.Long'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tvalue: 800\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t- field:\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tname: 'name'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\ttype: 'java.lang.String'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tvalue: 'Earth'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t- field:\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tname: 'waterPercent'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\ttype: 'int'\r\n" +
                        "\t\t\t\t\t\t\t\t\t\t\tvalue: 2\r\n" +
                        "\t\t\t\t\t\t- item:\r\n" +
                        "\t\t\t\t\t\t\ttype: 'null'\r\n" +
                        "\t\t\t\t\t\t\tvalue: null\r\n"; // Тотально типизированный null-маркер коллекции

        StringReader stringReader = new StringReader(inputYaml);
        System.err.println(inputYaml);
        Collection<Object> deserializedObjects = yamlReader.readObjects(stringReader);

        // Верификация корневых инвариантов СУБД
        assertNotNull(deserializedObjects, "Ридер выдал null вместо коллекции объектов!");
        assertEquals(1, deserializedObjects.size(), "Обязан быть десериализован ровно 1 корневой объект!");

        Object rootObj = deserializedObjects.iterator().next();
        assertTrue(rootObj instanceof XdStarSystem, "Корневой инстанс обязан быть XdStarSystem!");

        XdStarSystem system = (XdStarSystem) rootObj;
        assertEquals("system-100", system.getId());
        assertEquals("Sol-'System'", system.getNewName(), "🚨 Ошибка: Одинарные кавычки распакованы неверно!");

        // Глубокая верификация вложенной коллекции планет
        Collection<XdPlanet> planets = system.getPlanets();
        assertNotNull(planets, "Коллекция планет не должна быть null!");
        assertEquals(2, planets.size(), "В коллекции должно быть 2 элемента (планета + явный null-маркер)!");

        List<XdPlanet> planetsList = new ArrayList<>(planets);
        XdPlanet planet = planetsList.get(0);
        assertNotNull(planet, "Первый элемент коллекции не должен быть null!");
        assertEquals(Long.valueOf(800L), planet.getId(), "Рефлексивный боксинг типа Long поврежден!");
        assertEquals("Earth", planet.getName());
        assertEquals(2, planet.getWaterPercent(), "Примитив int искажен при чтении!");
        assertNull(planet.getSatellite(), "Пропущенное в полях метаданных свойство обязано остаться null!");

        // Проверка каноничного - item: type: 'null'
        assertNull(planetsList.get(1), "Явный объектный маркер '- item: type: null' не превратился в Java null!");
    }

    @Test
    @DisplayName("Кейс 2: Новые тесты — Проверка fail-safe устойчивости при нарушении корневого маркера")
    public void testReadObjects_CorruptedRootMarker_ShouldReturnEmptyCollection() throws Exception {
        String corruptedYaml =
                "corrupted_root_node:\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdStarSystem'\r\n";

        StringReader stringReader = new StringReader(corruptedYaml);
        Collection<Object> result = yamlReader.readObjects(stringReader);

        assertNotNull(result, "При битом заголовке СУБД обязана вернуть пустую коллекцию, а не null!");
        assertTrue(result.isEmpty(), "Коллекция обязана быть пустой при невалидном корневом маркере!");
    }

    @Test
    @DisplayName("Кейс 3: Новые тесты — Пустой поток объектов objects: без элементов")
    public void testReadObjects_EmptyObjectsBlock_ShouldReturnEmptyCollection() throws Exception {
        String emptyYaml = "objects:\r\n";
        StringReader stringReader = new StringReader(emptyYaml);
        Collection<Object> result = yamlReader.readObjects(stringReader);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Пустой блок objects: обязан возвращать чистый пустой ArrayList!");
    }

    @Test
    @DisplayName("Кейс 4: Новые тесты — Устойчивость к мусорным строкам и комментариям вне структуры")
    public void testReadObjects_TrashLinesAndNoise_ShouldIgnoreAndParseValidBlocks() throws Exception {
        String trashYaml =
                "# Кастомный системный комментарий СУБД\r\n" +
                        "objects:\r\n" +
                        "  # Еще один шум\r\n" +
                        "\t- object:\r\n" +
                        "\t\tclass: 'org.flib.xdstorage.entities.XdStarSystem'\r\n" +
                        "\t\tfields:\r\n" +
                        "\t\t\t- field:\r\n" +
                        "\t\t\t\tname: 'id'\r\n" +
                        "\t\t\t\ttype: 'java.lang.String'\r\n" +
                        "\t\t\t\tvalue: 'system-trash-proof'\r\n";

        StringReader stringReader = new StringReader(trashYaml);
        Collection<Object> result = yamlReader.readObjects(stringReader);

        assertNotNull(result);
        assertEquals(1, result.size(), "Парсер обязан пропустить неструктурированный шум и вычитать объект!");
        XdStarSystem system = (XdStarSystem) result.iterator().next();
        assertEquals("system-trash-proof", system.getId());
    }
}
