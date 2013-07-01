package org.flib.xdstore;

public class XmlDataStoreException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2872726377110939735L;

	public XmlDataStoreException() {
		super();
	}

	protected XmlDataStoreException(final String message, final Throwable cause, final boolean enableSuppression,
	        final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreException(final String message) {
		super(message);
	}

	public XmlDataStoreException(final Throwable cause) {
		super(cause);
	}
}
