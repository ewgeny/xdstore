package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;
import java.lang.reflect.Array;

/**
 * Стратегия сериализации любых массивов (включая примитивные типы данных).
 */
public class ArrayWriteStrategy implements XdStorageXmlWriteStrategy {

    @Override
    public boolean supports(final Class<?> clazz, final Object value) {
        return clazz.isArray();
    }

    @Override
    public void write(final String name, final Class<?> clazz, final Object value, final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<array");
        if (name != null) {
            writer.append(" name=\"").append(name).append("\"");
        }

        final int length = Array.getLength(value);
        writer.append(" class=\"").append(clazz.getComponentType().getName()).append("\" length=\"")
                .append(String.valueOf(length)).append("\">");

        for (int i = 0; i < length; ++i) {
            final Object element = Array.get(value, i);
            if (element == null) {
                context.writeIndent(writer, level + 1);
                writer.append("<object/>");
            } else {
                context.writeNextObject(null, element.getClass(), element, writer, level + 1);
            }
        }

        context.writeIndent(writer, level);
        writer.append("</array>");
    }
}
