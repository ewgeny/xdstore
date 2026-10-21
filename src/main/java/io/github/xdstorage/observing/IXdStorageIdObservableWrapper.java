package io.github.xdstorage.observing;

public interface IXdStorageIdObservableWrapper {

    void addObserver(XdStorageAbstractIdObserver observer);

    /**
     * Внутренний (завернутый) объект обертки. Позволяет генерируемому equals()
     * сравнивать обертки без рефлекшна на каждый вызов: equals — горячий путь
     * HashSet/HashMap движка.
     */
    default Object getWrappedObject__() {
        return null;
    }

}
