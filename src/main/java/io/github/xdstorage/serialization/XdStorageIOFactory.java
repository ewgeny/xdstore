package io.github.xdstorage.serialization;

import io.github.xdstorage.idgeneration.IXdStorageIdGenerator;
import io.github.xdstorage.serialization.yaml.XdStorageYamlIOFactory;
import io.github.xdstorage.services.XdStorageServicesLocator;

public class XdStorageIOFactory {
    private XdStorageIOFactory() {

    }

    public static IXdStorageIOFactory instanceIOFactory(XdStorageServicesLocator serviceLocator, IXdStorageIdGenerator idGenerator, FilesFormat format) {
        IXdStorageIOFactory ioFactory;
        switch (format) {
            case XML:
                ioFactory = new XdStorageDefaultIOFactory(serviceLocator, idGenerator);
                break;
            case YAML:
            default:
                ioFactory = new XdStorageYamlIOFactory(serviceLocator, idGenerator);
        }
        return ioFactory;
    }
}
