package org.flib.xdstorage.exceptions;

public class XdStorageConnectionException extends Exception {

    public XdStorageConnectionException() {
        super();
    }

    protected XdStorageConnectionException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public XdStorageConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public XdStorageConnectionException(final String message) {
        super(message);
    }

    public XdStorageConnectionException(final Throwable cause) {
        super(cause);
    }
}
