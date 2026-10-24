package io.github.xdstorage.observing;

/**
 * Contract of the generated id-observable wrapper: a delegating wrapper subclass that
 * keeps a list of {@link XdStorageAbstractIdObserver}s and notifies them when the
 * wrapped object's id is set (deferred-id notification used by the observer service).
 *
 * <p>Implementations are produced at runtime by {@code XdStorageObservableWrapperClassCodeGenerator}.
 * The wrapper delegates state access to the wrapped object; the observer list is
 * guarded by the wrapper instance. Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageIdObservableWrapper {

    /**
     * Registers an observer to be notified when this wrapper's id is set.
     *
     * @param observer the observer to register
     */
    void addObserver(XdStorageAbstractIdObserver observer);

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
