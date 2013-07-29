package org.flib.xdstore.transaction;

import org.flib.xdstore.XmlDataStoreException;

public class XmlDataStoreReadException extends XmlDataStoreException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4451068328718226707L;

	public XmlDataStoreReadException() {
		super();
	}

	protected XmlDataStoreReadException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreReadException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreReadException(String message) {
		super(message);
	}

	public XmlDataStoreReadException(Throwable cause) {
		super(cause);
	}
}
