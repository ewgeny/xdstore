package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageDefaultObjectsWriter.
 */
public class XdStorageDefaultObjectsWriterTest {

    private XdStorageServicesLocator mockServices;
    private IXdStorageSimpleTypeHelper mockTypeHelper;
    private IXdStorageIdGenerator mockIdGenerator;

    private XdStorageDefaultObjectsWriter objectsWriter;

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockTypeHelper = mock(IXdStorageSimpleTypeHelper.class);
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        objectsWriter = new XdStorageDefaultObjectsWriter(mockServices, mockTypeHelper, mockIdGenerator);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testWriteObjects_WithEmptyCollection_ShouldWriteValidXmlSkeleton() throws XdStorageIOException {
        StringWriter writer = new StringWriter();
        Collection<Object> emptyList = new ArrayList<>();

        objectsWriter.writeObjects(writer, emptyList);

        String resultXml = writer.toString();
        assertNotNull(resultXml);
        // Проверяем наличие базовых деклараций XML структуры
        assertTrue(resultXml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(resultXml.contains("<objects>"));
        assertTrue(resultXml.contains("</objects>"));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testWriteObjects_WithNullWriter_ShouldThrowXdStorageIOException() {
        Collection<Object> emptyList = new ArrayList<>();

        // Граничное условие: Передача null в качестве целевого потока записи Writer
        assertThrows(XdStorageIOException.class, () -> {
            objectsWriter.writeObjects(null, emptyList);
        });
    }

    @Test
    public void testWriteObjects_WithNullCollection_ShouldThrowXdStorageIOException() {
        StringWriter writer = new StringWriter();

        // Граничное условие: Попытка сериализовать null-коллекцию
        assertThrows(XdStorageIOException.class, () -> {
            objectsWriter.writeObjects(writer, null);
        });
    }
}
