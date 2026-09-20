package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamReader;

/**
 * Контракт для изолированной стратегии чтения конкретного XML-тега.
 */
public interface XdStorageXmlReadStrategy {

    /**
     * Проверяет, поддерживает ли данная стратегия текущий XML-элемент.
     */
    boolean supports(String elementName);

    /**
     * Извлекает и десериализует объект из текущего положения XML-потока.
     */
    Object read(String[] fieldName, XMLStreamReader xmlReader, XdStorageXmlReaderContext context) throws Exception;
}
