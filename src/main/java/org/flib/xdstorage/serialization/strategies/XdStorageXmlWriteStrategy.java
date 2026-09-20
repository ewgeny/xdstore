package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;

public interface XdStorageXmlWriteStrategy {
    /**
     * Проверяет, подходит ли стратегия для переданного типа Java-объекта.
     */
    boolean supports(Class<?> clazz, Object value);

    /**
     * Сериализует объект в поток Writer.
     */
    void write(String name, Class<?> clazz, Object value, Writer writer, int level, XdStorageXmlWriterContext context) throws Exception;
}
