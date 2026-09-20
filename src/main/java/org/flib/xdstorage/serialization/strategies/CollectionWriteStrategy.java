package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;
import java.util.Collection;

public class CollectionWriteStrategy implements XdStorageXmlWriteStrategy {
    @Override public boolean supports(Class<?> cl, Object val) { return val instanceof Collection; }

    @Override
    public void write(String name, Class<?> cl, Object value, Writer writer, int level, XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<collection name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\">");

        for (Object item : (Collection<?>) value) {
            if (item == null) {
                context.writeIndent(writer, level + 1);
                writer.append("<object/>");
            } else {
                context.writeNextObject(null, item.getClass(), item, writer, level + 1);
            }
        }
        context.writeIndent(writer, level);
        writer.append("</collection>");
    }
}
