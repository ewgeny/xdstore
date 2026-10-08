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
 * Высокоуровневый транзакционный диспетчер маршаллинга YAML.
 * Делегирует рефлексивный обход специализированным суб-компонентам.
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
            XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);
            emitter.openBlock("objects");

            for (final Object object : objects) {
                if (object == null) continue;
                emitter.openBlock("- object");
                XdStorageYamlBlockObjectsWriter.writeObjectData(object, emitter, services, simpleTypeHelper, idGenerator);
                emitter.closeBlock();
            }

            emitter.closeBlock();
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeReferences(final Writer writer, final XdStorageObjectIdField field, final Collection<Object> references) throws XdStorageIOException {
        try {
            XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(writer);
            emitter.openBlock("references");

            for (final Object reference : references) {
                if (reference == null) continue;
                emitter.openBlock("- reference");
                XdStorageYamlBlockObjectsWriter.writeReferenceData(reference, field, emitter);
                emitter.closeBlock();
            }

            emitter.closeBlock();
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }
}
