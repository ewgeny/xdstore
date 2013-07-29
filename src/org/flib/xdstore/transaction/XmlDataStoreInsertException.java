package org.flib.xdstore.transaction;

import org.flib.xdstore.XmlDataStoreException;

public class XmlDataStoreInsertException extends XmlDataStoreException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8337615136815791473L;

	public XmlDataStoreInsertException() {
		super();
	}

	protected XmlDataStoreInsertException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public XmlDataStoreInsertException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlDataStoreInsertException(String message) {
		super(message);
	}

	public XmlDataStoreInsertException(Throwable cause) {
		super(cause);
	}
}
