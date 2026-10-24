package io.github.xdstorage.resource;

/**
 * W-4c (docs/tasks/2.1.0/32, W4-DESIGN.md): a resource whose phase-1 writes go through a
 * commit journal can checkpoint — a full-file rewrite of the committed state plus journal
 * reset — without a transaction. Implemented by {@link XdStorageResource} (data/reference
 * resources) and the hash id-index resource; the resources manager calls it best-effort at
 * storage shutdown, so after any CLEAN close every journal is empty and a later power loss
 * loses nothing even with {@code durableWrites=false}.
 */
public interface IXdStorageJournalCheckpoint {

    /**
     * Checkpoints the journal if one exists and is non-empty. Best-effort: returns
     * {@code false} when there is nothing to checkpoint, when the resource was never
     * prepared in this run (its journal belongs to the NEXT open's replay — checkpointing
     * an unprepared, empty cache would destroy the base file), or when the resource is
     * busy with a live commit. Never throws.
     *
     * @return {@code true} when the journal was checkpointed (full committed-state rewrite
     *         plus journal reset), {@code false} when there was nothing to do
     */
    boolean checkpointJournalOnShutdown();
}
