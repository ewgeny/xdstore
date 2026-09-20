package org.flib.xdstorage.structure.update;

import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.search.IXdStorageSearchManager;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

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
