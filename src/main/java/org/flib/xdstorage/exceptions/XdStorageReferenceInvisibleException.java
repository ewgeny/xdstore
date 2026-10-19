package org.flib.xdstorage.exceptions;

/**
 * R-58: ссылка невидима В СНАПШОТЕ читателя (запись существует, но закоммичена
 * после начала транзакции читателя / откатена). Это НЕ повреждение данных — это
 * штатный ответ MVCC: «в вашей версии графа этого элемента нет». Гидрация
 * ленивых коллекций сгенерированных врапперов пропускает такие элементы
 * (элемент просто не входит в видимую версию графа), в отличие от
 * {@link XdStorageException}, которая означает реальную висячую ссылку.
 */
public class XdStorageReferenceInvisibleException extends XdStorageException {

    public XdStorageReferenceInvisibleException(final String message) {
        super(message);
    }

    public XdStorageReferenceInvisibleException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
