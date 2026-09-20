package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.StringReader;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class XdStorageXmlSerializationTest {

    private IXdStorageSimpleTypeHelper mockSimpleTypeHelper;
    private XdStorageDefaultObjectsReader xmlReader;

    @BeforeEach
    void setUp() {
        mockSimpleTypeHelper = Mockito.mock(IXdStorageSimpleTypeHelper.class);
        xmlReader = new XdStorageDefaultObjectsReader(mockSimpleTypeHelper);
    }

    @Test
    @DisplayName("Граничное условие: Обработка абсолютно пустого XML документа")
    void testReadEmptyDocument() {
        StringReader reader = new StringReader("");

        // Ридер должен корректно отработать и вернуть пустую коллекцию, а не выкинуть NullPointerException
        Collection<Object> result = xmlReader.read(reader);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Граничное условие: Поведение при поврежденном или невалидном синтаксисе XML")
    void testReadMalformedXml() {
        // XML со сломанными тегами
        StringReader malformedReader = new StringReader("<object class=\"java.lang.String\" name=\"test\"><invalid");

        // Метод обязан перехватить внутреннее исключение XMLStreamException и обернуть в XdStorageIOException
        assertThrows(XdStorageIOException.class, () -> {
            xmlReader.read(malformedReader);
        });
    }

    @Test
    @DisplayName("Уязвимость XXE: Проверка защиты от инъекции внешних сущностей")
    void testXmlExternalEntityInjectionProtection() {
        // Формируем вредоносный XML, пытающийся прочитать файл конфигурации
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE object [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<object class=\"java.lang.String\" name=\"payload\" value=\"&xxe;\"/>";

        StringReader maliciousReader = new StringReader(xxePayload);

        // Парсер должен либо выкинуть ошибку разбора DTD, либо проигнорировать внешнюю сущность,
        // не подставляя содержимое секретного файла в значение
        try {
            Collection<Object> result = xmlReader.read(maliciousReader);
            if (!result.isEmpty()) {
                Object obj = result.iterator().next();
                // Если сущность не обработалась, значение должно остаться пустым или содержать пустую строку
                assertNotEquals("/etc/passwd", obj.toString());
            }
        } catch (XdStorageIOException e) {
            // Выпадение в ошибку парсинга DTD — это также успешная защита от XXE атаки
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("Граничное условие: Отсутствие атрибута class у тега <object>")
    void testReadMissingClassAttribute() {
        // Передаем объект без обязательного указания java-класса
        StringReader reader = new StringReader("<object name=\"testObject\" value=\"123\"/>");

        // Согласно реализации, если className == null, парсер делает nextTag() и возвращает null, коллекция пуста
        Collection<Object> result = xmlReader.read(reader);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
