package org.flib.xdstorage.idgeneration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageDefaultIdGenerator implements IXdStorageIdGenerator {

    private static final Logger log = LogManager.getLogger(XdStorageDefaultIdGenerator.class);

    private final XdStorageServicesLocator services;

    private final Map<Class<?>, IXdStorageIdGenerator> generatorsByIdClassName = new HashMap<>();

    private final Map<Class<? extends IXdStorageIdGenerator>, IXdStorageIdGenerator> generatorsByClassName = new ConcurrentHashMap();

    public XdStorageDefaultIdGenerator(final XdStorageServicesLocator services) {
        this.services = services;

        generatorsByIdClassName.put(String.class, new XdStorageStringIdGenerator());
        generatorsByIdClassName.put(Long.class, new XdStorageLongIdGenerator(services));
        generatorsByIdClassName.put(Integer.class, new XdStorageIntegerIdGenerator(services));
    }

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageObjectIdField idField = clInfo.getIdField();
        IXdStorageIdGenerator generator = generatorsByIdClassName.get(idField.getFieldInfo().getValueClass());
        if (idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR
                && idField.getIdGeneratorClass() != null) {
            generator = registerIfNotExistAndGet(idField.getIdGeneratorClass());
        }
        if (generator == null)
            throw new XdStorageRuntimeException("cannot findUnidentified identifier generator for " + idField.getFieldInfo().getValueClass());
        return generator.generate(cl, storage, transaction);
    }

    private IXdStorageIdGenerator registerIfNotExistAndGet(Class<? extends IXdStorageIdGenerator> idGeneratorClass) {
        IXdStorageIdGenerator generator = generatorsByClassName.get(idGeneratorClass);
        if(generator == null) {
            try {
                generatorsByClassName.putIfAbsent(idGeneratorClass, idGeneratorClass.newInstance());
                generator = generatorsByClassName.get(idGeneratorClass);
            } catch (final IllegalAccessException | InstantiationException e) {
                log.error("cannot create instance of id generator " + idGeneratorClass, e);
            }
        }
        return generator;
    }
}
