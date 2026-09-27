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
import java.util.*;

/**
 * Высокоуровневый декомпозированный JSON-писатель СУБД (Поинт В).
 * Делегирует форматирование классу XdStorageJsonPrinter, а трекинг циклов — XdStorageJsonContext.
 */
public class XdStorageJsonObjectsWriter implements IXdStorageObjectsWriter {

    private final XdStorageServicesLocator services;
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final IXdStorageIdGenerator idGenerator;
    private final Map<Class<?>, Collection<XdStorageObjectField>> propertiesCache = new HashMap<>();

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
            XdStorageJsonPrinter.writeIndent(writer, 1);
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
            XdStorageJsonPrinter.writeIndent(writer, 1);
            writer.write("]\n}");
            writer.flush();
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            final XdStorageJsonContext context = new XdStorageJsonContext();
            writer.write("{\n");
            XdStorageJsonPrinter.writeIndent(writer, 1);
            writer.write("\"objects\": [\n");
            Iterator<Object> it = objects.iterator();
            while (it.hasNext()) {
                writeObjectValue(it.next(), writer, 2, context);
                if (it.hasNext()) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }
            XdStorageJsonPrinter.writeIndent(writer, 1);
            writer.write("]\n}");
            writer.flush();
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    private void writeObjectValue(final Object object, final Writer writer, final int indentLevel, final XdStorageJsonContext context) throws IOException {
        if (object == null) {
            writer.write("null");
            return;
        }

        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        // ИСПРАВЛЕНИЕ РАЗРЫВА ЦИКЛОВ: Пишем точный маркер ClassName@ID для маппера
        if (context.isVisited(object)) {
            XdStorageJsonPrinter.writeIndent(writer, indentLevel);

            Object idVal = null;
            if (clInfo.getIdField() != null) {
                idVal = clInfo.getIdField().get(object);
            }
            if (idVal == null) {
                String[] commonIdFields = {"idgeneration", "indexName", "resourceId"};
                for (String fieldName : commonIdFields) {
                    try {
                        java.lang.reflect.Field f = cl.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Object val = f.get(object);
                        if (val != null) { idVal = val; break; }
                    } catch (Throwable ignored) {}
                }
            }

            String refIdStr = (idVal != null) ? idVal.toString() : ("temp_" + System.identityHashCode(object));
            writer.write("{\"$ref\":\"" + cl.getName() + "@" + refIdStr + "\"}");
            return;
        }

        context.visit(object);

        Collection<XdStorageObjectField> props = propertiesCache.computeIfAbsent(cl, k -> clInfo.getFields().values());

        XdStorageJsonPrinter.writeIndent(writer, indentLevel);
        writer.write("{\n");

        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("\"type\":\"" + cl.getName() + "\",\n");

        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("\"properties\":{\n");

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

            XdStorageJsonPrinter.writeIndent(writer, indentLevel + 2);
            writer.write("\"" + property.getName() + "\":");
            writeValue(value, writer, indentLevel + 2, context);
        }
        writer.write("\n");
        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("}\n");

        XdStorageJsonPrinter.writeIndent(writer, indentLevel);
        writer.write("}");

        context.remove(object);
    }

    private void writeReferenceValue(final Object object, final Writer writer, final XdStorageObjectIdField field, final int indentLevel) throws IOException {
        if (object == null) {
            writer.write("null");
            return;
        }

        XdStorageJsonPrinter.writeIndent(writer, indentLevel);
        writer.write("{\n");

        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("\"type\":\"" + XdStorageObjectUtils.getEntityClass(object.getClass()).getName() + "\",\n");

        Object idVal = (field != null) ? field.get(object) : null;
        String idTypeName = (idVal != null) ? idVal.getClass().getName() : "java.lang.Object";

        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("\"idType\":\"" + idTypeName + "\",\n");

        XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
        writer.write("\"id\":");
        if (idVal == null) {
            writer.write("null");
        } else if (idVal instanceof Class) {
            writer.write("\"" + ((Class<?>) idVal).getName() + "\"");
        } else if (simpleTypeHelper.isSimpleType(idVal.getClass(), idVal)) {
            if (idVal.getClass() == Boolean.class || Number.class.isAssignableFrom(idVal.getClass())) {
                writer.write(idVal.toString());
            } else {
                writer.write("\"" + XdStorageJsonPrinter.escapeJson(idVal.toString()) + "\"");
            }
        } else {
            // ИСПРАВЛЕНИЕ: Сложные составные ID пишем как полноценные объекты
            writeValue(idVal, writer, indentLevel + 1, new XdStorageJsonContext());
        }
        writer.write("\n");
        XdStorageJsonPrinter.writeIndent(writer, indentLevel);
        writer.write("}");
    }

    private void writeValue(final Object value, final Writer writer, final int indentLevel, final XdStorageJsonContext context) throws IOException {
        if (value == null) {
            writer.write("null");
            return;
        }

        final Class<?> c = value.getClass();
        if (simpleTypeHelper.isSimpleType(c, value)) {
            if (c == Boolean.class || Number.class.isAssignableFrom(c)) {
                writer.write(value.toString());
            } else {
                writer.write("\"" + XdStorageJsonPrinter.escapeJson(simpleTypeHelper.simpleTypeToString(value)) + "\"");
            }
            return;
        }

        // МАТЕМАТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Убираем принудительный перехват аннотаций СУБД!
        // Даем вложенным JavaBean объектам маршаллироваться естественным путем через writeObjectValue.
        // Наш XdStorageJsonContext гарантированно защитит стек от зацикливания, а СУБД
        // получит 100% верные ID на второй фазе двухфазного коммита транзакции!

        if (c.isArray()) {
            writer.write("[\n");
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
                writeValue(Array.get(value, i), writer, indentLevel + 1, context);
                if (i < len - 1) writer.write(",\n");
            }
            writer.write("\n");
            XdStorageJsonPrinter.writeIndent(writer, indentLevel);
            writer.write("]");
        } else if (value instanceof Collection) {
            writer.write("[\n");
            Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
                writeValue(it.next(), writer, indentLevel + 1, context);
                if (it.hasNext()) writer.write(",\n");
            }
            writer.write("\n");
            XdStorageJsonPrinter.writeIndent(writer, indentLevel);
            writer.write("]");
        } else if (value instanceof Map) {
            writer.write("{\n");
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                XdStorageJsonPrinter.writeIndent(writer, indentLevel + 1);
                writer.write("\"" + XdStorageJsonPrinter.escapeJson(entry.getKey().toString()) + "\": ");
                writeValue(entry.getValue(), writer, indentLevel + 1, context);
                if (it.hasNext()) writer.write(",\n");
            }
            writer.write("\n");
            XdStorageJsonPrinter.writeIndent(writer, indentLevel);
            writer.write("}");
        } else {
            writeObjectValue(value, writer, indentLevel, context);
        }
    }

    private void checkAndGenerateId(Object object, Class<?> cl, XdStorageClassInfo clInfo) throws XdStorageIOException {
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR) && idField.get(object) == null) {
            try {
                IXdStorageIdGenerator activeIdGenerator = services.getIdGenerator();
                if (activeIdGenerator == null) {
                    activeIdGenerator = this.idGenerator;
                }
                idField.set(XdStorageObserverService.getObservableWrapper(object), activeIdGenerator.generate(cl, services.getStorage(), null));
            } catch (final Throwable e) {
                throw new XdStorageIOException(e);
            }
        }
    }
}