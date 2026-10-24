package io.github.xdstorage;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The physical storage policy of an entity class — where the engine puts its records on
 * disk. Declared per class with {@code @XdStorageObjectPolicy} (default:
 * {@link #StoreWithParentObject}).
 *
 * <p>Policies used as examples in this javadoc:</p>
 * <ul>
 *   <li>{@link #StoreAsClassObjects} — independent resource: an id index exists, so
 *       {@code has}/{@code load(Class, id)} answer from it;</li>
 *   <li>{@link #StoreAsSingleObject} — independent resource: one file per object;</li>
 *   <li>{@link #StoreWithParentObject} — embedded: the object has no resource of its own;
 *       it is persisted inside its parent's record and is not addressable by id
 *       ({@code save}/{@code load} on it directly fail loudly).</li>
 * </ul>
 */
public enum XdStoragePolicy {

    /**
     * Embedded type: the object is stored inside its parent object's record and has no
     * independent disk resource — it is not addressable by id on its own. The default
     * policy for classes without an explicit {@code @XdStorageObjectPolicy}.
     */
    StoreWithParentObject,

    /**
     * One object — one file: each stored object gets an individual file on disk plus an
     * entry in the class's references resource. Suited to few, large objects.
     */
    StoreAsSingleObject,

    /**
     * All stored objects of the class are batched into the class's shared file(s)
     * (fragmented by the open-time fragment size) with a class-level id index. The
     * default for typical entity workloads and the baseline of the published benchmarks.
     */
    StoreAsClassObjects;

    // Immutable set of the policies that own an independent disk (DAO) resource,
    // for fast lock-free access from the engine runtime.
    private static final Set<XdStoragePolicy> INDEPENDENT_POLICIES =
            Collections.unmodifiableSet(EnumSet.of(StoreAsSingleObject, StoreAsClassObjects));

    /**
     * Whether this policy gives the class an independent disk resource (its own DAO files
     * and id index) — {@code false} only for the embedded {@link #StoreWithParentObject}.
     *
     * @return {@code true} for {@link #StoreAsSingleObject} and {@link #StoreAsClassObjects}
     */
    public boolean isIndependentResource() {
        return INDEPENDENT_POLICIES.contains(this);
    }

    /**
     * Whether this is the embedded policy ({@link #StoreWithParentObject}): the object
     * lives inside its parent's record.
     *
     * @return {@code true} only for {@link #StoreWithParentObject}
     */
    public boolean isEmbedded() {
        return this == StoreWithParentObject;
    }
}
