package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*; // Предварительно предполагаем наличие Mockito для заглушек сервисов

class XdStorageSerializationBoundaryTest {

    private XdStorageDefaultObjectsWriter xmlWriter;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() {
        stringWriter = new StringWriter();
        XdStorageServicesLocator mockLocator = mock(XdStorageServicesLocator.class);
        IXdStorageIdGenerator mockGenerator = mock(IXdStorageIdGenerator.class);

        xmlWriter = new XdStorageDefaultObjectsWriter(
                mockLocator,
                new XdStorageDefaultSimpleTypeHelper(),
                mockGenerator
        );
    }

    @Test
    @DisplayName("Граничное условие: Сериализация пустой коллекции")
    void testEmptyCollectionSerialization() throws Exception {
        List<Object> emptyList = Collections.emptyList();
        List<Object> container = new ArrayList<>();
        container.add(emptyList);

        xmlWriter.writeObjects(stringWriter, container);
        String xmlResult = stringWriter.toString();

        // Проверяем корректность закрытия тегов пустой коллекции
        assertTrue(xmlResult.contains("<collection"), "Отсутствует тег коллекции");
        assertTrue(xmlResult.contains("</collection>"), "Тег коллекции не закрыт корректно");
    }

    @Test
    @DisplayName("Граничное условие: Сериализация пустой структуры Map")
    void testEmptyMapSerialization() throws Exception {
        xmlWriter.writeObjects(stringWriter, Collections.singletonList(new HashMap<>()));
        String xmlResult = stringWriter.toString();

        assertTrue(xmlResult.contains("<map"), "Отсутствует тег мапы");
        assertTrue(xmlResult.contains("</map>"), "Тег мапы не закрыт");
    }

    @Test
    @DisplayName("Граничное условие: Массив нулевой длины")
    void testZeroLengthArraySerialization() throws Exception {
        String[] emptyArray = new String[0];
        xmlWriter.writeObjects(stringWriter, Collections.singletonList(emptyArray));
        String xmlResult = stringWriter.toString();

        assertTrue(xmlResult.contains("length=\"0\""), "Неверно указана длина пустого массива");
    }
}
