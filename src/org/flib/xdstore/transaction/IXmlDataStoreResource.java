package org.flib.xdstore.transaction;

public interface IXmlDataStoreResource {

	String getResourceId();

	void prepare(XmlDataStoreTransaction transaction);

	void commit(XmlDataStoreTransaction transaction);

	void rollback(XmlDataStoreTransaction transaction);

	void release();

}
