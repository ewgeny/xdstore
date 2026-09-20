package org.flib.xdstorage.exceptions;

public class XdStorageException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -2872726377110939735L;

    public XdStorageException() {
        super();
    }

    protected XdStorageException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public XdStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public XdStorageException(final String message) {
        super(message);
    }

    public XdStorageException(final Throwable cause) {
        super(cause);
    }
}
