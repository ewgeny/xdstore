package io.github.xdstorage.exceptions;

/**
 * R-58: ссылка невидима В СНАПШОТЕ читателя (запись существует, но закоммичена
 * после начала транзакции читателя / откатена). Это НЕ повреждение данных — это
 * штатный ответ MVCC: «в вашей версии графа этого элемента нет». Гидрация
 * ленивых коллекций сгенерированных врапперов пропускает такие элементы
 * (элемент просто не входит в видимую версию графа), в отличие от
 * {@link XdStorageException}, которая означает реальную висячую ссылку.
 */
public class XdStorageReferenceInvisibleException extends XdStorageException {

    /** Creates the exception with a message.
     *
     * @param message the detail message
     */
    public XdStorageReferenceInvisibleException(final String message) {
        super(message);
    }

    /** Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public XdStorageReferenceInvisibleException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
