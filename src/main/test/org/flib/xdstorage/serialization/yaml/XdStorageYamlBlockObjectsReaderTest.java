package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Юнит-тесты: Декомпозированный парсер объектов YAML")
public class XdStorageYamlBlockObjectsReaderTest {

    private IXdStorageSimpleTypeHelper simpleTypeHelper;

    @BeforeEach
    public void setUp() {
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
    }

    @Test
    @DisplayName("Тест объектов: Проверка рефлексивной сборки и автоматического боксинга примитивов")
    public void testReadObject_PrimitiveBoxing_ShouldPopulateFieldsCorrectly() throws Exception {
        String yamlObject =
                "\t\tobject:\r\n" +
                        "\t\t\tclass: 'org.flib.xdstorage.entities.XdPlanet'\r\n" +
                        "\t\t\tid: '950'\r\n" +
                        "\t\t\tname: 'Mars'\r\n" +
                        "\t\t\twaterPercent: 0\r\n"; // Примитивный int без кавычек

        XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(new StringReader(yamlObject));
        tokenizer.nextToken(); // Потребляем маркер 'object:'

        Object result = XdStorageYamlBlockObjectsReader.readObject(tokenizer, 2, simpleTypeHelper);

        assertNotNull(result);
        assertTrue(result instanceof XdPlanet);
        XdPlanet planet = (XdPlanet) result;

        assertEquals(Long.valueOf(950L), planet.getId());
        assertEquals("Mars", planet.getName());
        assertEquals(0, planet.getWaterPercent(), "Нормализация примитива int дала сбой!");
    }

    @Test
    @DisplayName("Тест ссылок: Адаптивный разбор Reference при изменении порядка полей (id перед class)")
    public void testReadReference_ShuffledFields_ShouldParseSuccessfully() throws Exception {
        // Намеренно меняем местами class и objectId, имитируя алфавитную HashMap Райтера
        String shuffledReferenceYaml =
                "\t\treference:\r\n" +
                        "\t\t\tobjectId: 'galaxy-milkyway'\r\n" +
                        "\t\t\tclass: 'org.flib.xdstorage.entities.XdGalaxy'\r\n";

        XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(new StringReader(shuffledReferenceYaml));

        // =========================================================================
        // СИНТАКСИЧЕСКИЙ ФИКС ТЕСТА: По боевому контракту каретки маркер 'reference:'
        // должен быть поглощен диспетчером. Продвигаем токенизатор на один шаг вперед!
        // =========================================================================
        YamlLineToken startToken = tokenizer.peekToken();
        int parentLevel = startToken.level; // Запоминаем уровень (2)
        tokenizer.nextToken(); // Сдвигаем каретку на внутренние свойства ссылки!

        XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(org.flib.xdstorage.entities.XdGalaxy.class);
        XdStorageObjectIdField idField = clInfo.getIdField();

        Object result = XdStorageYamlBlockObjectsReader.readReference(
                tokenizer, parentLevel, idField, simpleTypeHelper
        );

        assertNotNull(result, "Адаптивный цикл чтения пропустил или потерял токены!");
        assertEquals("org.flib.xdstorage.entities.XdGalaxy", XdStorageObjectUtils.getEntityClass(result.getClass()).getName());
    }

}
