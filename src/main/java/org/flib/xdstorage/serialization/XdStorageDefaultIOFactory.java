package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;

/**
 * Высокопроизводительная фабрика ввода-вывода СУБД (Поинт Г).
 * Реализует паттерн Singleton для читателя и писателя, гарантируя монолитность
 * сессионного кэша и полностью исключая "concurrent modification" из-за расхождения версий объектов.
 */
public class XdStorageDefaultIOFactory implements IXdStorageIOFactory {

    private final XdStorageServicesLocator services;
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final IXdStorageIdGenerator idGenerator;

    // СИНГЛТОН-КОНТУР: Фиксируем долгоживущие экземпляры JSON-движков СУБД
    private final IXdStorageObjectsReader jsonReaderInstance;
    private final IXdStorageObjectsWriter jsonWriterInstance;

    public XdStorageDefaultIOFactory(final XdStorageServicesLocator service, final IXdStorageIdGenerator idGenerator) {
        this(service, new XdStorageDefaultSimpleTypeHelper(), idGenerator);
    }

    public XdStorageDefaultIOFactory(final XdStorageServicesLocator services, final IXdStorageSimpleTypeHelper simpleTypeHelper,
                                     final IXdStorageIdGenerator idGenerator) {
        this.services = services;
        this.simpleTypeHelper = simpleTypeHelper;
        this.idGenerator = idGenerator;

        // Инстанцируем компоненты строго один раз при запуске базы данных
        this.jsonReaderInstance = new XdStorageJsonObjectsReader(simpleTypeHelper);
        this.jsonWriterInstance = new XdStorageJsonObjectsWriter(services, simpleTypeHelper, idGenerator);
    }

    @Override
    public IXdStorageObjectsReader newInstanceReader() {
        // Гарантируем возврат единого экземпляра ридера со сквозным sessionObjectsCache!
        return jsonReaderInstance;
    }

    @Override
    public IXdStorageObjectsWriter newInstanceWriter() {
        // Гарантируем возврат единого экземпляра райтера
        return jsonWriterInstance;
    }
}
