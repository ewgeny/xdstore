package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.Collection;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageDefaultObjectsReader.
 */
public class XdStorageDefaultObjectsReaderTest {

    private IXdStorageSimpleTypeHelper mockTypeHelper;
    private XdStorageDefaultObjectsReader objectsReader;

    @BeforeEach
    public void setUp() {
        mockTypeHelper = mock(IXdStorageSimpleTypeHelper.class);
        objectsReader = new XdStorageDefaultObjectsReader(mockTypeHelper);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testRead_WithEmptyXmlRoot_ShouldReturnEmptyCollection() throws XdStorageIOException {
        // Подаем пустой XML документ с корневым тегом объектов
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><objects></objects>";
        StringReader reader = new StringReader(xml);

        Collection<Object> result = objectsReader.read(reader);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Для пустого дерева объектов коллекция должна быть пустой");
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testRead_WithMalformedXml_ShouldThrowXdStorageIOException() {
        // Граничное условие: Сломанный/невалидный XML синтаксис
        String malformedXml = "<?xml version=\"1.0\"?><objects><object class=\"java.lang.String\" name=\"test\" value=\"123\""; // нет закрывающего тега
        StringReader reader = new StringReader(malformedXml);

        // Движок StAX XMLStreamReader должен упасть, а метод обязан перехватить Throwable и обернуть в XdStorageIOException
        assertThrows(XdStorageIOException.class, () -> {
            objectsReader.read(reader);
        });
    }

    @Test
    public void testRead_WithNullReader_ShouldThrowXdStorageIOException() {
        // Граничное условие: Передача null вместо валидного символьного потока Reader
        assertThrows(XdStorageIOException.class, () -> {
            objectsReader.read(null);
        });
    }
}
