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
 * Заменяет старый XML-райтер, генерируя компактный JSON.
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
            writer.write("{\"references\":[");
            Iterator<Object> it = references.iterator();
            while (it.hasNext()) {
                writeReferenceValue(it.next(), writer, field);
                if (it.hasNext()) writer.write(",");
            }
            writer.write("]}");
            writer.flush(); // Выталкиваем накопленный буфер на диск
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            writer.write("{\"objects\":[");
            Iterator<Object> it = objects.iterator();
            while (it.hasNext()) {
                writeObjectValue(it.next(), writer);
                if (it.hasNext()) writer.write(",");
            }
            writer.write("]}");
            writer.flush(); // Пакетированный сброс буфера памяти на диск при коммите транзакции
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    private void writeObjectValue(final Object object, final Writer writer) throws IOException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        Collection<XdStorageObjectField> props = propertiesCache.computeIfAbsent(cl, k -> clInfo.getFields().values());

        writer.write("{");
        writer.write("\"type\":\"" + cl.getName() + "\",\"properties\":{");

        Iterator<XdStorageObjectField> it = props.iterator();
        boolean first = true;
        while (it.hasNext()) {
            XdStorageObjectField property = it.next();
            if (property.isIdField()) {
                checkAndGenerateId(object, cl, clInfo);
            }

            final Object value = property.get(object);
            if (value == null) continue;

            if (!first) writer.write(",");
            first = false;

            writer.write("\"" + property.getName() + "\":");
            writeValue(value, writer);
        }
        writer.write("}}");
    }

    private void writeReferenceValue(final Object object, final Writer writer, final XdStorageObjectIdField field) throws IOException {
        writer.write("{");
        writer.write("\"type\":\"" + XdStorageObjectUtils.getEntityClass(object.getClass()).getName() + "\",");
        writer.write("\"idType\":\"" + field.get(object).getClass().getName() + "\",");

        Object idVal = field.get(object);
        writer.write("\"id\":");
        if (idVal instanceof Class) {
            writer.write("\"" + ((Class<?>) idVal).getName() + "\"");
        } else {
            writer.write("\"" + escapeJson(idVal.toString()) + "\"");
        }
        writer.write("}");
    }

    private void writeValue(final Object value, final Writer writer) throws IOException {
        final Class<?> c = value.getClass();
        if (simpleTypeHelper.isSimpleType(c, value)) {
            if (c == Boolean.class || Number.class.isAssignableFrom(c)) {
                writer.write(value.toString());
            } else {
                writer.write("\"" + escapeJson(simpleTypeHelper.simpleTypeToString(value)) + "\"");
            }
        } else if (c.isArray()) {
            writer.write("[");
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                writeValue(Array.get(value, i), writer);
                if (i < len - 1) writer.write(",");
            }
            writer.write("]");
        } else if (value instanceof Collection) {
            writer.write("[");
            Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                writeValue(it.next(), writer);
                if (it.hasNext()) writer.write(",");
            }
            writer.write("]");
        } else if (value instanceof Map) {
            writer.write("{");
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writer.write("\"" + escapeJson(entry.getKey().toString()) + "\":");
                writeValue(entry.getValue(), writer);
                if (it.hasNext()) writer.write(",");
            }
            writer.write("}");
        } else {
            writeObjectValue(value, writer);
        }
    }

    private void checkAndGenerateId(Object object, Class<?> cl, XdStorageClassInfo clInfo) throws XdStorageIOException {
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                && idField.get(object) == null) {
            try {
                // ИСПРАВЛЕНИЕ: Вместо фиксированного в конструкторе поля запрашиваем
                // актуальный, живой генератор ID из локатора сервисов СУБД динамически!
                // Это гарантирует, что многопоточный тест подсунет писателю MyLongIdGenerator, а не дефолтный пустой.
                IXdStorageIdGenerator activeIdGenerator = services.getIdGenerator();
                if (activeIdGenerator == null) {
                    activeIdGenerator = this.idGenerator; // Фолбэк на дефолт, если локатор пуст
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
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
