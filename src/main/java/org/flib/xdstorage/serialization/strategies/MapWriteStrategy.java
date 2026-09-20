package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;
import java.util.Map;

/**
 * Стратегия сериализации структур Map в формат XML.
 */
public class MapWriteStrategy implements XdStorageXmlWriteStrategy {

    @Override
    public boolean supports(final Class<?> clazz, final Object value) {
        return value instanceof Map;
    }

    @Override
    public void write(final String name, final Class<?> clazz, final Object value, final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<map");
        if (name != null) {
            writer.append(" name=\"").append(name).append("\"");
        }
        writer.append(" class=\"").append(clazz.getName()).append("\">");

        for (final Map.Entry<?, ?> pair : ((Map<?, ?>) value).entrySet()) {
            context.writeIndent(writer, level + 1);
            writer.append("<entry>");

            // Запись ключа (Key)
            final Object key = pair.getKey();
            if (key == null) {
                writeNullNode(writer, level + 2, context);
            } else {
                context.writeNextObject(null, key.getClass(), key, writer, level + 2);
            }

            // Запись значения (Value)
            final Object val = pair.getValue();
            if (val == null) {
                writeNullNode(writer, level + 2, context);
            } else {
                context.writeNextObject(null, val.getClass(), val, writer, level + 2);
            }

            context.writeIndent(writer, level + 1);
            writer.append("</entry>");
        }

        context.writeIndent(writer, level);
        writer.append("</map>");
    }

    private void writeNullNode(final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<object/>");
    }
}
