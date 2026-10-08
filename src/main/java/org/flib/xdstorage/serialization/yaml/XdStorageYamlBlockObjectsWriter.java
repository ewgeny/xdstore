package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Изолированный рефлексивный маршаллер JavaBeans-сущностей и ORM-ссылок СУБД.
 */
public class XdStorageYamlBlockObjectsWriter {

    private static final Map<Class<?>, Collection<XdStorageObjectField>> propertiesCache = new HashMap<>();

    public static void writeObjectData(final Object object, final XdStorageYamlEmitter emitter,
                                       final XdStorageServicesLocator services,
                                       final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                       final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        Collection<XdStorageObjectField> props = propertiesCache.get(cl);
        if (props == null) {
            propertiesCache.put(cl, props = clInfo.getFields().values());
        }

        emitter.writeKey("class");
        emitter.writeScalar("'" + cl.getName() + "'");

        for (final XdStorageObjectField property : props) {
            if (property.isIdField()) {
                final XdStorageObjectIdField idField = clInfo.getIdField();
                if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                        && idField.get(object) == null) {
                    idField.set(XdStorageObserverService.getObservableWrapper(object), idGenerator.generate(cl, services.getStorage(), null));
                }
            }

            final Object value = property.get(object);
            if (value == null) {
                continue; // ПОЛНЫЙ ПРОПУСК NULL-ПОЛЕЙ (Вариант 2)
            }

            final Class<?> c = value.getClass();

            if (simpleTypeHelper.isSimpleType(c, value)) {
                emitter.writeKey(property.getName());
                emitter.writeScalar("'" + encode(simpleTypeHelper.simpleTypeToString(value)) + "'");
            } else if (c.isEnum()) {
                XdStorageYamlBlockContainersWriter.writeEnum(property.getName(), c, value, emitter);
            } else if (value instanceof Collection<?>) {
                XdStorageYamlBlockContainersWriter.writeCollection(property.getName(), c, (Collection<?>) value, emitter, services, simpleTypeHelper, idGenerator);
            } else if (value instanceof Map<?, ?>) {
                XdStorageYamlBlockContainersWriter.writeMap(property.getName(), c, (Map<?, ?>) value, emitter, services, simpleTypeHelper, idGenerator);
            } else {
                writeSingleObjectOrReference(property.getName(), c, value, emitter, services, simpleTypeHelper, idGenerator);
            }
        }
    }

    public static void writeSingleObjectOrReference(final String propertyName, final Class<?> cl, final Object value,
                                                    final XdStorageYamlEmitter emitter,
                                                    final XdStorageServicesLocator services,
                                                    final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                                    final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(cl);
        final XdStorageClassInfo targetClInfo = XdStorageObjectUtils.getClassInfo(entityClass);
        final XdStoragePolicy policy = targetClInfo.getPolicy();

        if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
            emitter.openBlock(propertyName);
            emitter.openBlock("- object");
            writeObjectData(value, emitter, services, simpleTypeHelper, idGenerator);
            emitter.closeBlock();
            emitter.closeBlock();
        } else {
            final XdStorageObjectIdField targetIdField = targetClInfo.getIdField();
            emitter.openBlock(propertyName);
            emitter.openBlock("reference");
            emitter.writeKey("class");
            emitter.writeScalar("'" + entityClass.getName() + "'");
            emitter.writeKey("objectId");
            emitter.writeScalar("'" + targetIdField.get(value).toString() + "'");
            emitter.closeBlock();
            emitter.closeBlock();
        }
    }

    public static void writeReferenceData(final Object reference, final XdStorageObjectIdField field, final XdStorageYamlEmitter emitter) throws IOException {
        emitter.writeKey("class");
        emitter.writeScalar("'" + XdStorageObjectUtils.getEntityClass(reference.getClass()).getName() + "'");
        emitter.writeKey("objectId");
        emitter.writeScalar("'" + field.get(reference).toString() + "'");
    }

    public static String encode(final String value) {
        if (value == null) return null;
        return value.replace("'", "''");
    }
}
