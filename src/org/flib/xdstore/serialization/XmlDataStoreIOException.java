package org.flib.xdstore.serialization;

public class XmlDataStoreIOException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8734889070625205556L;

	public XmlDataStoreIOException() {
		super();
	}

	protected XmlDataStoreIOException(String message, Throwable cause, boolean enableSuppression,
	        boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreIOException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreIOException(String message) {
		super(message);
	}

	public XmlDataStoreIOException(Throwable cause) {
		super(cause);
	}
}
