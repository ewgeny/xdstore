package org.flib.xdstore;

public class XmlDataStoreRuntimeException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4833619775508098085L;

	public XmlDataStoreRuntimeException() {
		super();
	}

	protected XmlDataStoreRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreRuntimeException(String message) {
		super(message);
	}

	public XmlDataStoreRuntimeException(Throwable cause) {
		super(cause);
	}
}
