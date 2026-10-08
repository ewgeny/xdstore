package org.flib.xdstorage.serialization;

import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.serialization.yaml.XdStorageYamlIOFactory;
import org.flib.xdstorage.services.XdStorageServicesLocator;

public class XdStorageIOFactory {
    private XdStorageIOFactory() {

    }

    public static IXdStorageIOFactory instanceIOFactory(XdStorageServicesLocator serviceLocator, IXdStorageIdGenerator idGenerator, FilesFormat format) {
        IXdStorageIOFactory ioFactory;
        switch (format) {
            case JSON:
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
