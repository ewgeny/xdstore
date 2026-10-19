package org.flib.xdstorage.code;

public interface IXdStorageSimpleWrapper {

    Object getObjectId__();

    /**
     * Внутренний (завернутый) объект обертки. Позволяет генерируемому equals()
     * сравнивать обертки без рефлекшна на каждый вызов: equals — горячий путь
     * HashSet/HashMap движка.
     */
    default Object getWrappedObject__() {
        return null;
    }

    void setReference__(boolean reference);

    boolean isReference__();

    void lock__();

    void unlock__();
}
