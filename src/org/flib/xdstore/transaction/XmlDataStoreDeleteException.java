package org.flib.xdstore.transaction;

import org.flib.xdstore.XmlDataStoreException;

public class XmlDataStoreDeleteException extends XmlDataStoreException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8772612242824434621L;

	public XmlDataStoreDeleteException() {
		super();
	}

	protected XmlDataStoreDeleteException(String message, Throwable cause, boolean enableSuppression,
	        boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreDeleteException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreDeleteException(String message) {
		super(message);
	}

	public XmlDataStoreDeleteException(Throwable cause) {
		super(cause);
	}
}
