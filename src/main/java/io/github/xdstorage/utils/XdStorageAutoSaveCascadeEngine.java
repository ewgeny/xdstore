package io.github.xdstorage.utils;

import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.index.IXdStorageIndexDaoResource;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageAutoSaveFieldInfo.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AUTOSAVE (docs/tasks/2.1.0/22) cascade engine — the walk that upserts
 * {@link XdStorageAutoSave @XdStorageAutoSave} annotated children after their parent's own
 * persistence. Called at the tail of the facade save/update; child saves re-enter the
 * facade's public save/update (the guards are idempotent), so triggers, observers, id
 * generation, rollback and MVCC behave exactly as for explicit saves.
 *
 * <p>State: a per-thread IDENTITY visited set plus a depth counter. The set lives for
 * exactly ONE top-level save/update (the depth counter detects the outermost cascade and
 * clears the set at its end) — a set that leaked across operations would silently skip every
 * later cascade on the same thread. The set is what makes reference cycles terminate and
 * duplicate references (the same instance via two fields) save once.
 *
 * <p>Shapes: COLLECTION elements and single REFERENCE values cascade as entities (a missing
 * id-field fails loudly in the save, the standard contract); MAP-typed fields cascade only
 * their VALUES whose class carries an id-field — the keys are never candidates, and
 * id-field-less values are skipped with a one-time WARN per class.
 */
public final class XdStorageAutoSaveCascadeEngine {

    private static final Logger log = LoggerFactory.getLogger(XdStorageAutoSaveCascadeEngine.class);

    private static final ThreadLocal<Set<Object>> VISITED = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** WARN-once registry for map values without an id-field (skipped by the cascade). */
    private static final Set<Class<?>> ID_FIELDLESS_WARNED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    /** WARN-once registry for embedded-element fields (StoreWithParentObject — no-op). */
    private static final Set<Class<?>> EMBEDDED_WARNED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    private XdStorageAutoSaveCascadeEngine() {
    }

    /**
     * The cascade entry — called at the tail of the facade save/update AFTER the object's own
     * persistence. The depth counter detects the outermost cascade and clears the visited set
     * when the whole operation unwinds.
     */
    public static void cascadeAfterPersist(final Object object, final IXdStorageTransaction tx,
                                           final XdStorageServicesLocator services,
                                           final IXdFileStorage facade)
            throws io.github.xdstorage.exceptions.XdStorageException,
            io.github.xdstorage.exceptions.XdStorageConnectionException {
        final boolean outermost = DEPTH.get() == 0;
        DEPTH.set(DEPTH.get() + 1);
        try {
            Set<Object> visited = VISITED.get();
            if (visited == null) {
                visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                VISITED.set(visited);
            }
            cascadeFields(object, tx, services, facade, visited);
        } finally {
            final int depth = DEPTH.get() - 1;
            DEPTH.set(depth);
            if (depth == 0) {
                VISITED.remove();
            }
        }
    }

    private static void cascadeFields(final Object object, final IXdStorageTransaction tx,
                                      final XdStorageServicesLocator services,
                                      final IXdFileStorage facade,
                                      final Set<Object> visited)
            throws io.github.xdstorage.exceptions.XdStorageException,
            io.github.xdstorage.exceptions.XdStorageConnectionException {
        final Object unwrapped = XdStorageObjectUtils.getWrappedObjectOrSameObject(object);
        if (!visited.add(unwrapped)) {
            return; // fully processed earlier in this operation (dedup + cycle guard)
        }
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(
                XdStorageObjectUtils.getEntityClass(unwrapped.getClass()));
        for (final XdStorageAutoSaveFieldInfo info : clInfo.getAutoSaveFields()) {
            for (final Object child : info.childrenOf(unwrapped)) {
                final Object childUnwrapped = XdStorageObjectUtils.getWrappedObjectOrSameObject(child);
                // dedup + cycle guard: the mark happens here, and the recursive walk below
                // is what opens the child's OWN fields — pre-marking before save would make
                // the child's cascade walk early-return and break transitivity (observed)
                if (visited.contains(childUnwrapped)) {
                    continue;
                }
                final Class<?> childClass = XdStorageObjectUtils.getEntityClass(childUnwrapped.getClass());
                final XdStorageClassInfo childInfo = XdStorageObjectUtils.getClassInfo(childClass);
                if (childInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
                    // the parent's own write already embeds StoreWithParentObject children —
                    // a cascade upsert would fail (no independent resource); WARN once per class
                    if (EMBEDDED_WARNED.add(childClass)) {
                        log.warn("@XdStorageAutoSave field element {} uses policy StoreWithParentObject — "
                                + "already embedded in the parent's file; cascade-save is a no-op for it",
                                childClass.getName());
                    }
                    visited.add(childUnwrapped);
                    continue;
                }
                if (info.getShape() == Shape.MAP) {
                    // MAP support (task 22 revision): only value objects with an id-field are
                    // cascade candidates — the keys and id-field-less values are skipped
                    if (childInfo.getIdField() == null) {
                        if (ID_FIELDLESS_WARNED.add(childClass)) {
                            log.warn("@XdStorageAutoSave MAP values of type {} have no id-field — "
                                    + "skipped by the cascade (only id-field-bearing value objects are saved)",
                                    childClass.getName());
                        }
                        visited.add(childUnwrapped);
                        continue;
                    }
                }
                upsert(childUnwrapped, childInfo, tx, services, facade);
                cascadeFields(childUnwrapped, tx, services, facade, visited);
            }
        }
    }

    /** V1 upsert: id null → insert (generated id backfills the same instance); id set → update if the index knows it, insert otherwise. */
    private static void upsert(final Object child, final XdStorageClassInfo childInfo,
                               final IXdStorageTransaction tx, final XdStorageServicesLocator services,
                               final IXdFileStorage facade)
            throws io.github.xdstorage.exceptions.XdStorageException,
            io.github.xdstorage.exceptions.XdStorageConnectionException {
        final Object id = childInfo.getIdField() != null ? childInfo.getIdField().get(child) : null;
        if (id == null) {
            facade.save(child, tx);
            return;
        }
        if (childInfo.getPolicy() == XdStoragePolicy.StoreAsSingleObject) {
            // single-object children have no id-index: the update is authoritative for an
            // app-assigned id (a first-time save of an id-set child is not the cascade's job)
            facade.update(child, tx);
            return;
        }
        final IXdStorageIndexDaoResource indexResource =
                services.getResourcesManager().lockIndexResource(childInfo, (io.github.xdstorage.transaction.XdStorageTransaction) tx);
        if (indexResource.has(id, (io.github.xdstorage.transaction.XdStorageTransaction) tx)) {
            facade.update(child, tx);
        } else {
            facade.save(child, tx);
        }
    }
}
