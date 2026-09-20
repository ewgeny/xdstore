package org.flib.xdstorage.structure.update;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.index.hash.XdStorageHashIndexResource;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageResource;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class XdStorageAbstractStructureUpdater<T> implements IXdStorageStructureUpdater<T> {

    private final String updateName;

    private XdStorageUpdateStructureResult result;

    private String message;

    private Throwable e;

    protected XdStorageAbstractStructureUpdater(final String updateName) {
        this.updateName = updateName;
    }

    @Override
    public String getUpdateName() {
        return updateName;
    }

    @Override
    public void setResult(final XdStorageUpdateStructureResult result, final String message, final Throwable e) {
        this.result = result;
        this.message = message;
        this.e = e;
    }

    @Override
    public XdStorageUpdateStructureResult getResult() {
        return result;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Throwable getError() {
        return e;
    }

    @Override
    public XdStorageUpdateStructureResult execute(final XdStorageAbstractResourcesManager resourcesManager,
                                                  final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = getDataClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();

        final AtomicBoolean success = new AtomicBoolean(true);
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final Set<String> handledResources = new HashSet<>();

            final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, transaction);
            if (resource instanceof XdStorageHashIndexResource) {
                ((XdStorageHashIndexResource) resource).readAsData(transaction).forEach(object -> {
                    final String resourceId = (String) object.getProperty("resourceId");

                    if (!success.get() || handledResources.contains(resourceId)) {
                        return;
                    }

                    handledResources.add(resourceId);

                    final IXdStorageDaoResource objectsResource;
                    try {
                        objectsResource = resourcesManager.lockResource(resourceId, clInfo, transaction);
                        if (objectsResource instanceof XdStorageResource) {
                            ((XdStorageResource) objectsResource).readAsData(transaction).parallelStream().forEach(data -> {
                                if (!success.get()) {
                                    return;
                                }

                                final T convertedObject = transform(data);
                                try {
                                    objectsResource.update(convertedObject, transaction);
                                } catch (final XdStorageException | XdStorageConnectionException e1) {
                                    success.set(false);
                                    e = e1;
                                }
                            });
                        }
                    } catch (final XdStorageException | XdStorageConnectionException e1) {
                        e = e1;
                        success.set(false);
                    }
                });
            }
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, transaction);
            referencesResource.readAsData(transaction).parallelStream().forEach(referenceData -> {
                if (!success.get()) {
                    return;
                }

                final Object id = referenceData.getId();

                try {
                    final IXdStorageDaoResource objectResource = resourcesManager.lockObjectResource(clInfo, id, transaction);
                    objectResource.readAsData(transaction).stream().forEach(data -> {
                        final T convertedObject = transform(data);
                        try {
                            objectResource.update(convertedObject, transaction);
                        } catch (final XdStorageException | XdStorageConnectionException e1) {
                            success.set(false);
                            e = e1;
                        }
                    });
                } catch (final XdStorageException | XdStorageConnectionException e1) {
                    success.set(false);
                    e = e1;
                }
            });
        } else {
            success.set(false);
            message = "Cannot update objects of " + cl + ". Storage policy cannot be " + XdStoragePolicy.StoreWithParentObject + ".";
        }

        return success.get() ? XdStorageUpdateStructureResult.SUCCESSFULLY : XdStorageUpdateStructureResult.FAILURE;
    }

    protected abstract T transform(final XdStorageIdentifiableObject oldStateOfObject);
}
