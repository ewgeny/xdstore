package org.flib.xdstorage.serialization.strategies;

import java.io.Writer;
import java.util.Collection;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

/**
 * Стратегия верхнего уровня для сериализации сложных рефлексивных объектов.
 * Автоматически рассчитывает политику хранения (инлайн объекта или запись как ссылки reference).
 */
public class ComplexObjectWriteStrategy implements XdStorageXmlWriteStrategy {

    private final IXdStorageIdGenerator idGenerator;
    private final XdStorageServicesLocator services;

    public ComplexObjectWriteStrategy(final IXdStorageIdGenerator idGenerator, final XdStorageServicesLocator services) {
        this.idGenerator = idGenerator;
        this.services = services;
    }

    @Override
    public boolean supports(final Class<?> clazz, final Object value) {
        // Подходит для любых объектов, прошедших сито простых типов, массивов и коллекций
        return true;
    }

    @Override
    public void write(final String name, final Class<?> clazz, final Object value, final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(value.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(entityClass);
        final XdStoragePolicy policy = clInfo.getPolicy();

        // Проверяем политику: если объект должен лежать независимо (не StoreWithParentObject) — пишем ссылку reference
        if (policy != null && policy != XdStoragePolicy.StoreWithParentObject && name != null) {
            writeReferenceNode(name, entityClass, value, clInfo.getIdField(), writer, level, context);
        } else {
            writeInlineObjectNode(name, entityClass, value, clInfo, writer, level, context);
        }
    }

    private void writeInlineObjectNode(final String name, final Class<?> cl, final Object obj, final XdStorageClassInfo clInfo, final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        context.writeIndent(writer, level);
        writer.append("<object");
        if (name != null) writer.append(" name=\"").append(name).append("\"");
        writer.append(" class=\"").append(cl.getName()).append("\">");

        final Collection<XdStorageObjectField> props = clInfo.getFields().values();
        for (final XdStorageObjectField property : props) {

            // Если поле является ID и требует автогенерации — генерируем ключ до сериализации
            if (property.isIdField()) {
                final XdStorageObjectIdField idField = clInfo.getIdField();
                if (idField.get(obj) == null && (idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)) {
                    idField.set(XdStorageObserverService.getObservableWrapper(obj),
                            idGenerator.generate(cl, services.getStorage(), null));
                }
            }

            final Object propValue = property.get(obj);
            if (propValue == null) {
                context.writeIndent(writer, level + 1);
                writer.append("<object name=\"").append(property.getName()).append("\"/>");
                continue;
            }

            // Передаем маршалинг вложенного свойства дальше по цепочке контекста
            context.writeNextObject(property.getName(), propValue.getClass(), propValue, writer, level + 1);
        }

        context.writeIndent(writer, level);
        writer.append("</object>");
    }

    private void writeReferenceNode(final String name, final Class<?> cl, final Object obj, final XdStorageObjectIdField idField, final Writer writer, final int level, final XdStorageXmlWriterContext context) throws Exception {
        // Генерация ID в случае его отсутствия у сущности-ссылки
        if (idField.get(obj) == null) {
            idField.set(XdStorageObserverService.getObservableWrapper(obj), idGenerator.generate(cl, services.getStorage(), null));
        }

        final Object idValue = idField.get(obj);
        context.writeIndent(writer, level);
        writer.append("<reference name=\"").append(name).append("\" class=\"").append(cl.getName())
                .append("\" classObjectId=\"").append(idValue.getClass().getName()).append("\"");

        if (idValue.getClass() == Class.class) {
            writer.append(" objectId=\"").append(((Class<?>) idValue).getName()).append("\"/>");
        } else if (XdStorageObjectUtils.isSimpleType(idValue.getClass(), null)) {
            writer.append(" objectId=\"").append(idValue.toString()).append("\"/>");
        } else {
            // Граничное условие: если ID — составной объект, пишем его вложенным тегом
            writer.append("\">");
            context.writeNextObject(idField.getName(), idValue.getClass(), idValue, writer, level + 1);
            context.writeIndent(writer, level);
            writer.append("</reference>");
        }
    }
}
