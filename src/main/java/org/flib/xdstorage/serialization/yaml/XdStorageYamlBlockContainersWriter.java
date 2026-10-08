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

public class XdStorageYamlBlockContainersWriter {

    public static void writeEnum(final String name, final Class<?> cl, final Object value, final XdStorageYamlEmitter emitter) throws IOException {
        emitter.openBlock(name);
        emitter.openBlock("enum");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");
        emitter.writeKey("value");
        emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(value.toString()) + "'");
        emitter.closeBlock();
        emitter.closeBlock();
    }

    public static void writeCollection(final String name, final Class<?> cl, final Collection<?> collection,
                                       final XdStorageYamlEmitter emitter,
                                       final XdStorageServicesLocator services,
                                       final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                       final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock(name);
        emitter.openBlock("collection");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");

        for (final Object item : collection) {
            if (item == null) {
                emitter.writeScalar("- null");
            } else {
                writeContainerItem(item, emitter, services, simpleTypeHelper, idGenerator);
            }
        }
        emitter.closeBlock();
        emitter.closeBlock();
    }

    public static void writeMap(final String name, final Class<?> cl, final Map<?, ?> map,
                                final XdStorageYamlEmitter emitter,
                                final XdStorageServicesLocator services,
                                final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        emitter.openBlock(name);
        emitter.openBlock("map");
        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");

        for (final Map.Entry<?, ?> pair : map.entrySet()) {
            emitter.openBlock("- entry");

            // Кодируем метаданные типов прямо в плоские скалярные строки
            emitter.writeKey("key");
            emitter.writeScalar("'" + serializeMapItem(pair.getKey(), simpleTypeHelper) + "'");

            emitter.writeKey("value");
            emitter.writeScalar("'" + serializeMapItem(pair.getValue(), simpleTypeHelper) + "'");

            emitter.closeBlock();
        }
        closeMapBlocks(emitter);
    }

    private static void closeMapBlocks(final XdStorageYamlEmitter emitter) {
        emitter.closeBlock();
        emitter.closeBlock();
    }

    private static String serializeMapItem(final Object item, final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        if (item == null) return "null";
        Class<?> c = item.getClass();
        if (c == Object.class) {
            return "object:java.lang.Object";
        }
        if (c.isEnum()) {
            // Пишем префикс энума и его полное имя класса для динамического восстановления!
            return "enum:" + c.getName() + ":" + item.toString();
        }
        return XdStorageYamlBlockObjectsWriter.encode(simpleTypeHelper.simpleTypeToString(item));
    }

    private static void writeContainerItem(final Object item, final XdStorageYamlEmitter emitter,
                                           final XdStorageServicesLocator services,
                                           final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                           final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        final Class<?> c = item.getClass();
        if (simpleTypeHelper.isSimpleType(c, item)) {
            emitter.writeScalar("- '" + XdStorageYamlBlockObjectsWriter.encode(simpleTypeHelper.simpleTypeToString(item)) + "'");
        } else if (c.isEnum()) {
            emitter.openBlock("- enum");
            emitter.writeKey("class");
            emitter.writeScalar("'" + c.getName() + "'");
            emitter.writeKey("value");
            emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(item.toString()) + "'");
            emitter.closeBlock();
        } else {
            final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(c);
            final XdStorageClassInfo targetClInfo = XdStorageObjectUtils.getClassInfo(entityClass);
            final XdStoragePolicy policy = targetClInfo.getPolicy();

            if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
                emitter.openBlock("- object");
                XdStorageYamlBlockObjectsWriter.writeObjectData(item, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            } else {
                final XdStorageObjectIdField targetIdField = targetClInfo.getIdField();
                emitter.openBlock("- reference");
                emitter.writeKey("class");
                emitter.writeScalar("'" + entityClass.getName() + "'");
                emitter.writeKey("objectId");
                emitter.writeScalar("'" + targetIdField.get(item).toString() + "'");
                emitter.closeBlock();
            }
        }
    }
}
