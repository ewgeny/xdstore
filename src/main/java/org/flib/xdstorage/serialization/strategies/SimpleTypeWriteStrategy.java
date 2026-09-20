package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;

public class SimpleTypeWriteStrategy implements XdStorageXmlWriteStrategy {
    private final IXdStorageSimpleTypeHelper helper;
    public SimpleTypeWriteStrategy(IXdStorageSimpleTypeHelper helper) { this.helper = helper; }

    @Override public boolean supports(Class<?> cl, Object val) { return helper.isSimpleType(cl, val); }

    @Override
    public void write(String name, Class<?> cl, Object value, Writer writer, int level, XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<object");
        if (name != null) writer.append(" name=\"").append(name).append("\"");
        writer.append(" class=\"").append(cl.getName()).append("\" value=\"")
                .append(context.encodeText(helper.simpleTypeToString(value))).append("\"/>");
    }
}
