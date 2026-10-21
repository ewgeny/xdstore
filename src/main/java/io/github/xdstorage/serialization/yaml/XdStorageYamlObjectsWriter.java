package io.github.xdstorage.serialization.yaml;

import io.github.xdstorage.exceptions.XdStorageIOException;
import io.github.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import io.github.xdstorage.idgeneration.IXdStorageIdGenerator;
import io.github.xdstorage.serialization.IXdStorageObjectsWriter;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.utils.XdStorageObjectIdField;

import java.io.Writer;
import java.util.Collection;

/**
 * Главный фасад подсистемы маршаллинга СУБД.
 * Строго реализует интерфейс IXdStorageObjectsWriter и координирует потоковую запись.
 */
public class XdStorageYamlObjectsWriter implements IXdStorageObjectsWriter {

    private final XdStorageServicesLocator services;
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final IXdStorageIdGenerator idGenerator;

    public XdStorageYamlObjectsWriter(final XdStorageServicesLocator services,
                                      final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                      final IXdStorageIdGenerator idGenerator) {
        this.services = services;
        this.simpleTypeHelper = simpleTypeHelper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            final XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

            emitter.openBlock("objects");

            for (final Object obj : objects) {
                if (obj == null) continue;

                // Корневой уровень СУБД всегда пишет полноценные тела агрегатов.
                // Ссылочная ORM-политика применяется только при каскадном обходе свойств полей!
                emitter.openBlock("- object");
                XdStorageYamlBlockObjectsWriter.writeObjectData(obj, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            }

            emitter.closeBlock(); // Закрываем "objects"
            // S-1: сентинел конца файла — ридер обязан отказать в загрузке обрезанного файла
            emitter.writeEofSentinel();
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeReferences(final Writer writer, final XdStorageObjectIdField field, final Collection<Object> references) throws XdStorageIOException {
        try {
            final XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);

            emitter.openBlock("references");

            for (final Object ref : references) {
                if (ref == null) continue;

                // Контракт записи чистых транзакционных связей СУБД
                emitter.openBlock("- reference");
                XdStorageYamlBlockObjectsWriter.writeReferenceData(ref, field, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            }

            emitter.closeBlock(); // Закрываем "references"
            // S-1: сентинел конца файла — ридер обязан отказать в загрузке обрезанного файла
            emitter.writeEofSentinel();
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }
}
