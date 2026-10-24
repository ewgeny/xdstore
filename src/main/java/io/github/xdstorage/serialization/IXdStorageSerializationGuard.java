package io.github.xdstorage.serialization;

/**
 * R-43: the consistent-serialization contract for live objects. A resource flush writes
 * the LIVE instance (the canonical B+tree registry) while concurrent transactions mutate
 * its fields — splits/joins can tear a {@code LinkedList} right under the writer's
 * iterator (ConcurrentModificationException, torn output). A class implementing this
 * contract must keep its state stable for reading between the {@link
 * #beginConsistentSerialization()} and {@link #endConsistentSerialization()} calls.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageSerializationGuard {

    /** Зафиксировать состояние объекта на время сериализации (best-effort: при отказе — WARN и запись без замка). */
    void beginConsistentSerialization();

    /** Отпустить состояние объекта после сериализации. */
    void endConsistentSerialization();
}
