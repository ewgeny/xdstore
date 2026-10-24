package io.github.xdstorage.structure.update;

/**
 * The execution outcome of a structure update (migration) script, as reported by the
 * updater and journalled in {@code XdStorageUpdateStructureRecord}.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public enum XdStorageUpdateStructureResult {

    /**
     * The script completed: its journal record is persisted, so the structure manager
     * skips it on subsequent structure checks of the class.
     */
    SUCCESSFULLY,

    /**
     * The script failed or has not been executed: no journal record is persisted, so the
     * structure manager stops the update chain and re-runs the script on the next
     * structure check.
     */
    FAILURE;
}
