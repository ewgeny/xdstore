package io.github.xdstorage.index;

import io.github.xdstorage.resource.IXdStorageResourceObject;

/**
 * Specialization of {@link IXdStorageResourceObject} for the id-index resources: a
 * transactional resource whose DAO view is the id-index surface {@link
 * IXdStorageIndexDaoResource} instead of a plain data resource. Implemented by the hash
 * id-index resource family and by the B+tree id-index resource; the resources manager
 * locks, prepares and commits them like any other resource.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageIndexResourceObject extends IXdStorageResourceObject<IXdStorageIndexDaoResource> {

}
