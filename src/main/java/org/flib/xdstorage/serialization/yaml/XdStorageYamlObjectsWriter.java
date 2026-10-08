package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

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
                XdStorageYamlBlockObjectsWriter.writeReferenceData(ref, field, emitter);
                emitter.closeBlock();
            }

            emitter.closeBlock(); // Закрываем "references"
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }
}
