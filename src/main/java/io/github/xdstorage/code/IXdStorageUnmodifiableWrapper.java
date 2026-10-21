package io.github.xdstorage.code;

public interface IXdStorageUnmodifiableWrapper {

    /**
     * Внутренний (завернутый) объект обертки. Позволяет генерируемому equals()
     * сравнивать обертки без рефлекшна на каждый вызов: equals — горячий путь
     * HashSet/HashMap движка.
     */
    default Object getWrappedObject__() {
        return null;
    }

}
