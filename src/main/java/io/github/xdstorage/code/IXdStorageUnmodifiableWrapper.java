package io.github.xdstorage.code;

/**
 * Contract of the generated unmodifiable wrapper — the read-only wrapper subclass the
 * engine hands out for snapshot/MVCC views of stored entities (and for the
 * infrastructure base class via {@code XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator}).
 * Generated mutators reject or roll back any modification attempt.
 *
 * <p>Implementations are produced at runtime by the unmodifiable code generators;
 * {@code XdStorageDummyUnmodifiableWrapper} is the degraded stand-in used when no
 * runtime Java compiler is available. Stateless per instance.
 * Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageUnmodifiableWrapper {

    /**
     * Внутренний (завернутый) объект обертки. Позволяет генерируемому equals()
     * сравнивать обертки без рефлекшна на каждый вызов: equals — горячий путь
     * HashSet/HashMap движка.
     *
     * @return the wrapped (inner) entity object, or {@code null} when the implementation
     *         does not track it
     */
    default Object getWrappedObject__() {
        return null;
    }

}
