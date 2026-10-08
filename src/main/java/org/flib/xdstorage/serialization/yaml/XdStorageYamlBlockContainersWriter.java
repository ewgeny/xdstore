package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Выделенный stateless-маршаллер для форматирования Java Collections, Maps и Enums.
 * Реализует строгую объектную изоляцию ячеек с явным сохранением метаданных типов.
 */
public class XdStorageYamlBlockContainersWriter {

    public static void writeEnum(final String name, final Class<?> cl, final Object value, final XdStorageYamlEmitter emitter) throws IOException {
        emitter.openBlock(name);
        writeEnumInline(cl, value, emitter);
        emitter.closeBlock();
    }

    public static void writeEnumInline(final Class<?> cl, final Object value, final XdStorageYamlEmitter emitter) throws IOException {
        emitter.openBlock("enum");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");
        emitter.writeKey("value");
        emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(value.toString()) + "'");
        emitter.closeBlock();
    }

    public static void writeCollection(final String name, final Class<?> cl, final Collection<?> collection,
                                       final XdStorageYamlEmitter emitter,
                                       final XdStorageServicesLocator services,
                                       final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                       final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock(name);
        writeCollectionInline(cl, collection, emitter, services, simpleTypeHelper, idGenerator);
        emitter.closeBlock();
    }

    public static void writeCollectionInline(final Class<?> cl, final Collection<?> collection,
                                             final XdStorageYamlEmitter emitter,
                                             final XdStorageServicesLocator services,
                                             final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                             final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock("collection");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");

        for (final Object item : collection) {
            // Открываем каноничную объектную ячейку списка СУБД
            emitter.openBlock("- item");
            if (item == null) {
                emitter.writeKey("type");
                emitter.writeScalar("'null'");
                emitter.writeKey("value");
                emitter.writeScalar("null");
            } else {
                final Class<?> c = item.getClass();
                emitter.writeKey("type");
                emitter.writeScalar("'" + c.getName() + "'");

                // Нарезаем контент строго внутрь узла value:
                if (simpleTypeHelper.isSimpleType(c, item)) {
                    emitter.writeKey("value");
                    emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(simpleTypeHelper.simpleTypeToString(item)) + "'");
                } else if (c.isEnum()) {
                    emitter.openBlock("value");
                    writeEnumInline(c, item, emitter);
                    emitter.closeBlock();
                } else {
                    emitter.openBlock("value");
                    writeComplexItemInline(c, item, emitter, services, simpleTypeHelper, idGenerator);
                    emitter.closeBlock();
                }
            }
            emitter.closeBlock(); // Закрываем "- item"
        }
        emitter.closeBlock(); // Закрываем "collection"
    }

    public static void writeMap(final String name, final Class<?> cl, final Map<?, ?> map,
                                final XdStorageYamlEmitter emitter,
                                final XdStorageServicesLocator services,
                                final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock(name);
        writeMapInline(cl, map, emitter, services, simpleTypeHelper, idGenerator);
        emitter.closeBlock();
    }

    public static void writeMapInline(final Class<?> cl, final Map<?, ?> map,
                                      final XdStorageYamlEmitter emitter,
                                      final XdStorageServicesLocator services,
                                      final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                      final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock("map");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");

        for (final Map.Entry<?, ?> pair : map.entrySet()) {
            emitter.openBlock("- entry");

            // Выгружаем ключ как полноценную независимую type/value пару
            writeMapComponentNode("key", pair.getKey(), emitter, services, simpleTypeHelper, idGenerator);

            // Выгружаем значение как полноценную независимую type/value пару
            writeMapComponentNode("value", pair.getValue(), emitter, services, simpleTypeHelper, idGenerator);

            emitter.closeBlock(); // Закрываем "- entry"
        }
        emitter.closeBlock(); // Закрываем "map"
    }

    private static void writeMapComponentNode(final String nodeName, final Object item, final XdStorageYamlEmitter emitter,
                                              final XdStorageServicesLocator services,
                                              final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                              final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock(nodeName);
        if (item == null) {
            emitter.writeKey("type");
            emitter.writeScalar("'null'");
            emitter.writeKey("value");
            emitter.writeScalar("null");
        } else {
            final Class<?> c = item.getClass();
            emitter.writeKey("type");

            if (c == Object.class) {
                emitter.writeScalar("'java.lang.Object'");
                emitter.openBlock("value");
                emitter.openBlock("object");
                emitter.writeKey("class");
                emitter.writeScalar("'java.lang.Object'");
                emitter.closeBlock();
                emitter.closeBlock();
            } else {
                emitter.writeScalar("'" + c.getName() + "'");
                if (simpleTypeHelper.isSimpleType(c, item)) {
                    emitter.writeKey("value");
                    emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(simpleTypeHelper.simpleTypeToString(item)) + "'");
                } else if (c.isEnum()) {
                    emitter.openBlock("value");
                    writeEnumInline(c, item, emitter);
                    emitter.closeBlock();
                } else {
                    emitter.openBlock("value");
                    writeComplexItemInline(c, item, emitter, services, simpleTypeHelper, idGenerator);
                    emitter.closeBlock();
                }
            }
        }
        emitter.closeBlock(); // Закрываем nodeName (key или value)
    }

    private static void writeComplexItemInline(final Class<?> c, final Object item, final XdStorageYamlEmitter emitter,
                                               final XdStorageServicesLocator services,
                                               final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                               final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(c);
        final XdStorageClassInfo targetClInfo = XdStorageObjectUtils.getClassInfo(entityClass);
        final XdStoragePolicy policy = targetClInfo.getPolicy();

        if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
            emitter.openBlock("object");
            XdStorageYamlBlockObjectsWriter.writeObjectData(item, emitter, services, simpleTypeHelper, idGenerator);
            emitter.closeBlock();
        } else {
            final XdStorageObjectIdField targetIdField = targetClInfo.getIdField();
            emitter.openBlock("reference");
            emitter.writeKey("class");
            emitter.writeScalar("'" + entityClass.getName() + "'");
            emitter.writeKey("objectId");
            emitter.writeScalar("'" + targetIdField.get(item).toString() + "'");
            emitter.closeBlock();
        }
    }
}
