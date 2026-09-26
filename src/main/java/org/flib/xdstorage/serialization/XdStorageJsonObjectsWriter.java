package org.flib.xdstorage.serialization;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Высокопроизводительный JSON-писатель графа объектов СУБД (Поинт Г).
 * Генерирует форматированный (Pretty Printed) JSON с нулевой аллокацией отступов в куче.
 */
public class XdStorageJsonObjectsWriter implements IXdStorageObjectsWriter {

    private final XdStorageServicesLocator services;
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final IXdStorageIdGenerator idGenerator;
    private final Map<Class<?>, Collection<XdStorageObjectField>> propertiesCache = new HashMap<>();

    // РАЗГОН: Статический массив готовых отступов для исключения нагрузки на Garbage Collector
    private static final String[] INDENTS = new String[64];

    static {
        INDENTS[0] = "";
        String singleIndent = "  "; // 2 пробела на уровень
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < INDENTS.length; i++) {
            sb.append(singleIndent);
            INDENTS[i] = sb.toString();
        }
    }

    public XdStorageJsonObjectsWriter(final XdStorageServicesLocator services,
                                      final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                      final IXdStorageIdGenerator idGenerator) {
        this.services = services;
        this.simpleTypeHelper = simpleTypeHelper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void writeReferences(final Writer writer, final XdStorageObjectIdField field, final Collection<Object> references) throws XdStorageIOException {
        try {
            writer.write("{\n");
            writeIndent(writer, 1);
            writer.write("\"references\": [\n");
            Iterator<Object> it = references.iterator();
            while (it.hasNext()) {
                writeReferenceValue(it.next(), writer, field, 2);
                if (it.hasNext()) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }
            writeIndent(writer, 1);
            writer.write("]\n}");
            writer.flush();
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            writer.write("{\n");
            writeIndent(writer, 1);
            writer.write("\"objects\": [\n");
            Iterator<Object> it = objects.iterator();
            while (it.hasNext()) {
                writeObjectValue(it.next(), writer, 2);
                if (it.hasNext()) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }
            writeIndent(writer, 1);
            writer.write("]\n}");
            writer.flush();
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    private void writeObjectValue(final Object object, final Writer writer, final int indentLevel) throws IOException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        Collection<XdStorageObjectField> props = propertiesCache.computeIfAbsent(cl, k -> clInfo.getFields().values());

        writeIndent(writer, indentLevel);
        writer.write("{\n");

        writeIndent(writer, indentLevel + 1);
        writer.write("\"type\": \"" + cl.getName() + "\",\n");

        writeIndent(writer, indentLevel + 1);
        writer.write("\"properties\": {\n");

        Iterator<XdStorageObjectField> it = props.iterator();
        boolean first = true;
        while (it.hasNext()) {
            XdStorageObjectField property = it.next();
            if (property.isIdField()) {
                checkAndGenerateId(object, cl, clInfo);
            }

            final Object value = property.get(object);
            if (value == null) continue;

            if (!first) {
                writer.write(",\n");
            }
            first = false;

            writeIndent(writer, indentLevel + 2);
            writer.write("\"" + property.getName() + "\": ");
            writeValue(value, writer, indentLevel + 2);
        }
        writer.write("\n");
        writeIndent(writer, indentLevel + 1);
        writer.write("}\n");

        writeIndent(writer, indentLevel);
        writer.write("}");
    }

    private void writeReferenceValue(final Object object, final Writer writer, final XdStorageObjectIdField field, final int indentLevel) throws IOException {
        writeIndent(writer, indentLevel);
        writer.write("{\n");

        writeIndent(writer, indentLevel + 1);
        writer.write("\"type\": \"" + XdStorageObjectUtils.getEntityClass(object.getClass()).getName() + "\",\n");

        writeIndent(writer, indentLevel + 1);
        writer.write("\"idType\": \"" + field.get(object).getClass().getName() + "\",\n");

        Object idVal = field.get(object);
        writeIndent(writer, indentLevel + 1);
        writer.write("\"id\": ");
        if (idVal instanceof Class) {
            writer.write("\"" + ((Class<?>) idVal).getName() + "\"");
        } else {
            writer.write("\"" + escapeJson(idVal.toString()) + "\"");
        }
        writer.write("\n");
        writeIndent(writer, indentLevel);
        writer.write("}");
    }

    private void writeValue(final Object value, final Writer writer, final int indentLevel) throws IOException {
        final Class<?> c = value.getClass();
        if (simpleTypeHelper.isSimpleType(c, value)) {
            if (c == Boolean.class || Number.class.isAssignableFrom(c)) {
                writer.write(value.toString());
            } else {
                writer.write("\"" + escapeJson(simpleTypeHelper.simpleTypeToString(value)) + "\"");
            }
        } else if (c.isArray()) {
            writer.write("[\n");
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                writeIndent(writer, indentLevel + 1);
                writeValue(Array.get(value, i), writer, indentLevel + 1);
                if (i < len - 1) writer.write(",\n");
            }
            writer.write("\n");
            writeIndent(writer, indentLevel);
            writer.write("]");
        } else if (value instanceof Collection) {
            writer.write("[\n");
            Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                writeIndent(writer, indentLevel + 1);
                writeValue(it.next(), writer, indentLevel + 1);
                if (it.hasNext()) writer.write(",\n");
            }
            writer.write("\n");
            writeIndent(writer, indentLevel);
            writer.write("]");
        } else if (value instanceof Map) {
            writer.write("{\n");
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writeIndent(writer, indentLevel + 1);
                writer.write("\"" + escapeJson(entry.getKey().toString()) + "\": ");
                writeValue(entry.getValue(), writer, indentLevel + 1);
                if (it.hasNext()) writer.write(",\n");
            }
            writer.write("\n");
            writeIndent(writer, indentLevel);
            writer.write("}");
        } else {
            writeObjectValue(value, writer, indentLevel);
        }
    }

    private void writeIndent(final Writer writer, int level) throws IOException {
        if (level >= INDENTS.length) {
            level = INDENTS.length - 1;
        }
        writer.write(INDENTS[level]);
    }

    private void checkAndGenerateId(Object object, Class<?> cl, XdStorageClassInfo clInfo) throws XdStorageIOException {
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                && idField.get(object) == null) {
            try {
                IXdStorageIdGenerator activeIdGenerator = services.getIdGenerator();
                if (activeIdGenerator == null) {
                    activeIdGenerator = this.idGenerator;
                }
                idField.set(XdStorageObserverService.getObservableWrapper(object),
                        activeIdGenerator.generate(cl, services.getStorage(), null));
            } catch (final Throwable e) {
                throw new XdStorageIOException(e);
            }
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\r")
                .replace("\t", "\t");
    }
}