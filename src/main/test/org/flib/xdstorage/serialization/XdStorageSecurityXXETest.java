package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class XdStorageSecurityXXETest {

    private XdStorageDefaultObjectsReader xmlReader;

    @BeforeEach
    void setUp() {
        IXdStorageSimpleTypeHelper typeHelper = new XdStorageDefaultSimpleTypeHelper();
        xmlReader = new XdStorageDefaultObjectsReader(typeHelper);
    }

    @Test
    @DisplayName("Атака XXE: Ридер должен падать или игнорировать внешние сущности при попытке кражи файлов")
    void testExternalEntityInjectionDefense() {
        // XML-сформированный эксплоит, пытающийся прочитать приватный файл хоста
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE xml [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                "<objects>\n" +
                "  <object class=\"java.lang.String\" value=\"&xxe;\"/>\n" +
                "</objects>";

        StringReader reader = new StringReader(xxePayload);

        // Корректно настроенный безопасный парсер обязан выбросить исключение XMLStreamException,
        // завернутое в наше XdStorageIOException, пресекая атаку на корню
        assertThrows(XdStorageIOException.class, () -> {
            xmlReader.read(reader);
        }, "Парсер уязвим к XXE атакам! Необходимо отключить SUPPORT_DTD.");
    }

    @Test
    @DisplayName("Атака XML Bomb: Защита от отказа в обслуживании (Billion Laughs)")
    void testXmlBombDefense() {
        // Экстремальное каскадное раздувание сущностей в памяти для вызова OutOfMemory
        String xmlBombPayload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE lolz [\n" +
                " <!ENTITY lol \"lol\">\n" +
                " <!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
                " <!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;\">\n" +
                "]>\n" +
                "<objects>\n" +
                "  <object class=\"java.lang.String\" value=\"&lol2;\"/>\n" +
                "</objects>";

        StringReader reader = new StringReader(xmlBombPayload);

        assertThrows(XdStorageIOException.class, () -> {
            xmlReader.read(reader);
        }, "Парсер уязвим к атакам типа XML-бомба!");
    }
}
