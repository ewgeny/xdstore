package org.flib.xdstorage.serialization;

import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Вспомогательный утилитный класс для работы с XML-потоками StAX.
 * Инкапсулирует низкоуровневые операции с XMLStreamReader.
 */
public final class XdStorageXmlHelper {

    /**
     * Приватный конструктор для предотвращения инстанцирования утилитного класса.
     */
    private XdStorageXmlHelper() {
        // Запрет создания экземпляров
    }

    /**
     * Извлекает все атрибуты текущего XML-элемента из потока StAX и упаковывает их в Map.
     * Позволяет стратегиям десериализации получать доступ к атрибутам (например, name, class, value)
     * по ключу за время O(1) вместо многократного прогона циклов.
     *
     * @param xmlReader активный экземпляр XMLStreamReader, установленный на START_ELEMENT
     * @return Map, где ключ — локальное имя атрибута, а значение — строковое значение атрибута
     */
    public static Map<String, String> extractAttributes(final XMLStreamReader xmlReader) {
        final Map<String, String> attributes = new HashMap<>();
        final int count = xmlReader.getAttributeCount();

        for (int i = 0; i < count; ++i) {
            attributes.put(
                    xmlReader.getAttributeLocalName(i),
                    xmlReader.getAttributeValue(i)
            );
        }

        return attributes;
    }
}
