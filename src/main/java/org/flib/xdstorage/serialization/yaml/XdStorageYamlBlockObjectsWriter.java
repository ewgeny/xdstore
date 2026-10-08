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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Изолированный рефлексивный маршаллер JavaBeans-сущностей и ORM-ссылок СУБД.
 * Генерирует тотально типизированную структуру полей name/type/value.
 */
public class XdStorageYamlBlockObjectsWriter {

    private static final Map<Class<?>, Collection<XdStorageObjectField>> propertiesCache = new ConcurrentHashMap<>();

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

        // Открываем единый, строго структурированный узел для всех полей JavaBeans класса
        emitter.openBlock("fields");

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
                continue; // Полный пропуск null-свойств согласно инвариантам СУБД
            }

            final Class<?> fieldType = property.getFieldInfo().getClazz();
            final Class<?> c = value.getClass();

            // Открываем элемент списка мета-модели полей
            emitter.openBlock("- field");
            emitter.writeKey("name");
            emitter.writeScalar("'" + property.getName() + "'");
            emitter.writeKey("type");
            emitter.writeScalar("'" + fieldType.getName() + "'");

            // Запись контента уходит строго внутрь узла value:
            if (simpleTypeHelper.isSimpleType(c, value)) {
                emitter.writeKey("value");
                emitter.writeScalar("'" + encode(simpleTypeHelper.simpleTypeToString(value)) + "'");
            } else if (c.isEnum()) {
                emitter.openBlock("value");
                XdStorageYamlBlockContainersWriter.writeEnumInline(c, value, emitter);
                emitter.closeBlock();
            } else if (value instanceof Collection<?>) {
                emitter.openBlock("value");
                XdStorageYamlBlockContainersWriter.writeCollectionInline(c, (Collection<?>) value, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            } else if (value instanceof Map<?, ?>) {
                emitter.openBlock("value");
                XdStorageYamlBlockContainersWriter.writeMapInline(c, (Map<?, ?>) value, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            } else {
                emitter.openBlock("value");
                writeSingleObjectOrReferenceInline(c, value, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            }

            emitter.closeBlock(); // Закрываем "- field"
        }

        emitter.closeBlock(); // Закрываем "fields"
    }

    public static void writeSingleObjectOrReferenceInline(final Class<?> cl, final Object value,
                                                          final XdStorageYamlEmitter emitter,
                                                          final XdStorageServicesLocator services,
                                                          final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                                          final IXdStorageIdGenerator idGenerator) throws IOException, XdStorageException {
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(cl);
        final XdStorageClassInfo targetClInfo = XdStorageObjectUtils.getClassInfo(entityClass);
        final XdStoragePolicy policy = targetClInfo.getPolicy();

        if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
            emitter.openBlock("object");
            writeObjectData(value, emitter, services, simpleTypeHelper, idGenerator);
            emitter.closeBlock();
        } else {
            final XdStorageObjectIdField targetIdField = targetClInfo.getIdField();
            emitter.openBlock("reference");
            emitter.writeKey("class");
            emitter.writeScalar("'" + entityClass.getName() + "'");
            Object targetId = targetIdField.get(value);
            if (targetId != null) {
                emitter.writeKey("objectId");
                emitter.writeScalar("'" + targetId + "'");
            }
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
