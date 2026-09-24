package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;

public class XdStorageDefaultIOFactory implements IXdStorageIOFactory {

    private final XdStorageServicesLocator services;

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;

    private final IXdStorageIdGenerator idGenerator;

    public XdStorageDefaultIOFactory(final XdStorageServicesLocator service, final IXdStorageIdGenerator idGenerator) {
        this(service, new XdStorageDefaultSimpleTypeHelper(), idGenerator);
    }

    public XdStorageDefaultIOFactory(final XdStorageServicesLocator services, final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                     final IXdStorageIdGenerator idGenerator) {
        this.services = services;
        this.simpleTypeHelper = simpleTypeHelper;
        this.idGenerator = idGenerator;
    }

    @Override
    public IXdStorageObjectsReader newInstanceReader() {
        // Переключаем маршалинг ядра СУБД на легкий JSON!
        return new XdStorageJsonObjectsReader(simpleTypeHelper);
    }

    @Override
    public IXdStorageObjectsWriter newInstanceWriter() {
        // Переключаем маршалинг ядра СУБД на легкий JSON!
        return new XdStorageJsonObjectsWriter(services, simpleTypeHelper, idGenerator);
    }
}
