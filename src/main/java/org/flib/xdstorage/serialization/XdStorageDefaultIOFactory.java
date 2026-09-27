package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.services.XdStorageServicesLocator;

/**
 * Высокопроизводительная фабрика ввода-вывода СУБД (Поинт Г).
 * Использует паттерн ThreadLocal для полной потокоизоляции читателей и писателей XML,
 * что на 100% ликвидирует гонки данных в кэшах рефлексии метаданных СУБД.
 */
public class XdStorageDefaultIOFactory implements IXdStorageIOFactory {

    private final XdStorageServicesLocator services;
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final IXdStorageIdGenerator idGenerator;

    // ПОТОКОВАЯ ИЗОЛЯЦИЯ: Каждый поток воркера СУБД получает свой собственный,
    // изолированный экземпляр ридера и райтера со своими локальными кэшами свойств!
    private final ThreadLocal<IXdStorageObjectsReader> threadLocalReader = new ThreadLocal<>();
    private final ThreadLocal<IXdStorageObjectsWriter> threadLocalWriter = new ThreadLocal<>();

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
        // Ленивая инициализация ридера строго для текущего потока выполнения
        IXdStorageObjectsReader reader = threadLocalReader.get();
        if (reader == null) {
            reader = new XdStorageDefaultObjectsReader(simpleTypeHelper);
            threadLocalReader.set(reader);
        }
        return reader;
    }

    @Override
    public IXdStorageObjectsWriter newInstanceWriter() {
        // Ленивая инициализация райтера строго для текущего потока выполнения
        IXdStorageObjectsWriter writer = threadLocalWriter.get();
        if (writer == null) {
            writer = new XdStorageDefaultObjectsWriter(services, simpleTypeHelper, idGenerator);
            threadLocalWriter.set(writer);
        }
        return writer;
    }
}
