package io.github.xdstorage.structure.update;

import io.github.xdstorage.IXdStorageWatcher;
import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.index.IXdStorageIndexDaoResource;
import io.github.xdstorage.object.XdStorageIdentifiableObject;
import io.github.xdstorage.resource.IXdStorageDaoResource;
import io.github.xdstorage.resource.XdStorageAbstractResourcesManager;
import io.github.xdstorage.search.IXdStorageSearchManager;
import io.github.xdstorage.search.XdStorageSearchIndex;
import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.utils.XdStorageClassInfo;
import io.github.xdstorage.utils.XdStorageObjectIdField;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.Date;

public class XdStorageSearchIndexStructureUpdater extends XdStorageAbstractStructureUpdater<XdStorageSearchIndexRecord> {

    private final IXdStorageSearchManager searchManager;

    private final Class<?> cl;

    private final XdStorageSearchIndex index;

    public XdStorageSearchIndexStructureUpdater(final IXdStorageSearchManager searchManager, final Class<?> cl, final XdStorageSearchIndex index) {
        super(cl.getSimpleName() + "-" + index.getName() + "-" + new Date().getTime());

        this.searchManager = searchManager;

        this.cl = cl;
        this.index = index;
    }

    @Override
    public Class<?> getDataClass() {
        return cl;
    }

    @Override
    public XdStorageUpdateStructureResult execute(final XdStorageAbstractResourcesManager resourcesManager, final XdStorageTransaction transaction) throws XdStorageConnectionException {
        try {
            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            final XdStorageObjectIdField idField = clInfo.getIdField();

            final IXdStorageWatcher watcher = new IXdStorageWatcher() {
                @Override
                public void watch(final Object object) {
                    try {
                        searchManager.update(object, transaction);
                    } catch (final XdStorageException | XdStorageConnectionException e) {
                        throw new XdStorageRuntimeException("cannot update search index record for object " + idField.get(object) + " of " + cl, e);
                    }
                }
            };

            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource indexResource = resourcesManager.lockIndexResource(clInfo, transaction);
                indexResource.watch(transaction, watcher);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {

                final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, transaction);
                referencesResource.watch(transaction, new IXdStorageWatcher<Object>() {
                    @Override
                    public void watch(final Object object) {
                        try {
                            final IXdStorageDaoResource objectResource = resourcesManager.lockObjectResource(object, transaction);
                            objectResource.watch(transaction, watcher);
                        } catch (final XdStorageException | XdStorageConnectionException e) {
                            throw new XdStorageRuntimeException(e);
                        }
                    }
                });
            } else {
                throw new XdStorageException("cannot update index for " + cl);
            }
        } catch (final XdStorageException e) {
            throw new XdStorageRuntimeException("execute update exception", e);
        }
        return XdStorageUpdateStructureResult.SUCCESSFULLY;
    }

    @Override
    protected XdStorageSearchIndexRecord transform(final XdStorageIdentifiableObject oldStateOfObject) {
        return null;
    }
}
