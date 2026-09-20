package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.serialization.strategies.*;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Исправленная и потокобезопасная реализация райтера объектов СУБД в XML.
 */
public class XdStorageDefaultObjectsWriter implements IXdStorageObjectsWriter, XdStorageXmlWriterContext {

    private final List<XdStorageXmlWriteStrategy> strategies = new ArrayList<>();

    /**
     * ИСПРАВЛЕНИЕ RACE CONDITION: Замена HashMap на ConcurrentHashMap для безопасного
     * многопоточного кэширования метаданных полей при записи объектов.
     */
    private final Map<Class<?>, Collection<XdStorageObjectField>> propertiesCache = new ConcurrentHashMap<>();

    public XdStorageDefaultObjectsWriter(final XdStorageServicesLocator services,
                                         final IXdStorageSimpleTypeHelper simpleHelper,
                                         final IXdStorageIdGenerator idGenerator) {
        // Регистрация модулей вывода данных
        strategies.add(new SimpleTypeWriteStrategy(simpleHelper));
        strategies.add(new CollectionWriteStrategy());
        strategies.add(new MapWriteStrategy());
        strategies.add(new ArrayWriteStrategy());
        strategies.add(new ComplexObjectWriteStrategy(idGenerator, services));
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writeIndent(writer, 0);
            writer.write("<objects>");
            for (final Object object : objects) {
                if (object != null) {
                    writeNextObject(null, object.getClass(), object, writer, 1);
                }
            }
            writeIndent(writer, 0);
            writer.write("</objects>");
        } catch (final Throwable cause) {
            throw new XdStorageIOException("Ошибка записи объектов в XML-поток", cause);
        }
    }

    @Override
    public void writeNextObject(String name, Class<?> clazz, Object value, Writer writer, int level) throws Exception {
        for (int i = 0; i < strategies.size(); i++) {
            XdStorageXmlWriteStrategy strategy = strategies.get(i);
            if (strategy.supports(clazz, value)) {
                strategy.write(name, clazz, value, writer, level, this);
                return;
            }
        }
    }

    @Override
    public Collection<XdStorageObjectField> getFieldsCache(Class<?> clazz) {
        // Атомарное конкурентное наполнение кэша райтера
        return propertiesCache.computeIfAbsent(clazz,
                cl -> XdStorageObjectUtils.getClassInfo(cl).getFields().values());
    }

    @Override
    public void writeIndent(Writer writer, int level) throws Exception {
        writer.append("\r\n");
        for (int i = 0; i < level; ++i) {
            writer.append('\t');
        }
    }

    @Override
    public String encodeText(String value) {
        if (value == null) return null;
        StringBuilder sb = new java.lang.StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '<' || ch == '&' || ch == '>' || ch == '\"' || ch == '\'') {
                sb.append("&#").append((int) ch).append(";");
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    @Override
    public void writeReferences(Writer w, XdStorageObjectIdField f, Collection<Object> refs) {
        throw new UnsupportedOperationException("Перенесено в декомпозированную структуру ComplexObjectWriteStrategy");
    }
}
