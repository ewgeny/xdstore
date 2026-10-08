package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Юнит-тесты: Декомпозированный парсер контейнеров YAML")
public class XdStorageYamlBlockContainersReaderTest {

    private IXdStorageSimpleTypeHelper simpleTypeHelper;

    @BeforeEach
    public void setUp() {
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
    }

    @Test
    @DisplayName("Тест коллекций: Разбор ArrayList с примитивами и явными null-маркерами")
    public void testReadCollection_MixedPrimitivesAndNulls_ShouldRestoreCleanly() throws Exception {
        String yamlCollection =
                "\t\tcollection:\r\n" +
                        "\t\t\tclass: 'java.util.ArrayList'\r\n" +
                        "\t\t\t- 'Mercury'\r\n" +
                        "\t\t\t- null\r\n" +
                        "\t\t\t- 'Venus'\r\n";

        XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(new StringReader(yamlCollection));

        // Снайперская правка: фиксируем уровень и продвигаем каретку мимо 'collection:'
        YamlLineToken startToken = tokenizer.peekToken();
        int parentLevel = startToken.level;
        tokenizer.nextToken(); // Поглощаем 'collection:' для выравнивания каретки по боевому контракту!

        @SuppressWarnings("unchecked")
        Collection<Object> result = (Collection<Object>) XdStorageYamlBlockContainersReader.readCollection(
                tokenizer, parentLevel, simpleTypeHelper
        );

        assertNotNull(result, "Метод вернул null из-за смещения каретки!");
        assertEquals(3, result.size());

        List<Object> list = new ArrayList<>(result);
        assertEquals("Mercury", list.get(0));
        assertNull(list.get(1));
        assertEquals("Venus", list.get(2));
    }

    @Test
    @DisplayName("Тест мап: Разбор HashMap с текстовыми парами ключ-значение")
    public void testReadMap_StandardStringHashMap_ShouldRestoreProperties() throws Exception {
        // =========================================================================
        // ЭТАЛOННЫЙ СИНТАКСИС МАП СУБД: Приводим ручной YAML теста в полное
        // соответствие с боевым выводом декомпозированного Райтера (writeMap).
        // Ключи и значения являются вложенными блоками для поддержки сложных ORM-объектов!
        // =========================================================================
        String yamlMap =
                "\t\tmap:\r\n" +
                        "\t\t\tclass: 'java.util.HashMap'\r\n" +
                        "\t\t\t- entry:\r\n" +
                        "\t\t\t\tkey:\r\n" +
                        "\t\t\t\t\t- 'sector'\r\n" +
                        "\t\t\t\tvalue:\r\n" +
                        "\t\t\t\t\t- 'Sector-7G'\r\n" +
                        "\t\t\t- entry:\r\n" +
                        "\t\t\t\tkey:\r\n" +
                        "\t\t\t\t\t- 'status'\r\n" +
                        "\t\t\t\tvalue:\r\n" +
                        "\t\t\t\t\t- 'Active'\r\n";

        XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(new StringReader(yamlMap));

        YamlLineToken startToken = tokenizer.peekToken();
        int parentLevel = startToken.level;
        tokenizer.nextToken(); // Поглощаем 'map:'

        @SuppressWarnings("unchecked")
        Map<Object, Object> result = (Map<Object, Object>) XdStorageYamlBlockContainersReader.readMap(
                tokenizer, parentLevel
        );

        assertNotNull(result, "Мапа не восстановилась!");
        assertEquals(2, result.size());
        assertEquals("Sector-7G", result.get("sector"));
        assertEquals("Active", result.get("status"));
    }

    @Test
    @DisplayName("Тест пустых структур: Пустая коллекция не должна приводить к краху")
    public void testReadCollection_Empty_ShouldReturnEmptyInstance() throws Exception {
        String emptyCollectionYaml =
                "\t\tcollection:\r\n" +
                        "\t\t\tclass: 'java.util.ArrayList'\r\n";

        XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(new StringReader(emptyCollectionYaml));

        YamlLineToken startToken = tokenizer.peekToken();
        int parentLevel = startToken.level;
        tokenizer.nextToken(); // Поглощаем 'collection:'

        @SuppressWarnings("unchecked")
        Collection<Object> result = (Collection<Object>) XdStorageYamlBlockContainersReader.readCollection(
                tokenizer, parentLevel, simpleTypeHelper
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
