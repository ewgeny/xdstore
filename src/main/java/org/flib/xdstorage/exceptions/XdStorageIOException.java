package org.flib.xdstorage.exceptions;

public class XdStorageIOException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = -8734889070625205556L;

    public XdStorageIOException() {
        super();
    }

    protected XdStorageIOException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public XdStorageIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public XdStorageIOException(String message) {
        super(message);
    }

    public XdStorageIOException(Throwable cause) {
        super(cause);
    }
}
