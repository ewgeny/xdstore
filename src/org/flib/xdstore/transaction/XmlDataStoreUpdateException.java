package org.flib.xdstore.transaction;

import org.flib.xdstore.XmlDataStoreException;

public class XmlDataStoreUpdateException extends XmlDataStoreException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2156830378519355680L;

	public XmlDataStoreUpdateException() {
		super();
	}

	protected XmlDataStoreUpdateException(String message, Throwable cause, boolean enableSuppression,
	        boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreUpdateException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreUpdateException(String message) {
		super(message);
	}

	public XmlDataStoreUpdateException(Throwable cause) {
		super(cause);
	}
}
