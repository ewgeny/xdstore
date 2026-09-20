package org.flib.xdstorage.exceptions;

public class XdStorageRuntimeException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = -4833619775508098085L;

    public XdStorageRuntimeException() {
        super();
    }

    protected XdStorageRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public XdStorageRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public XdStorageRuntimeException(String message) {
        super(message);
    }

    public XdStorageRuntimeException(Throwable cause) {
        super(cause);
    }
}
