package org.flib.xdstore.transaction;

import java.util.Map;

public interface IXmlDataStoreResource {

	String getResourceId();

	void prepare(XmlDataStoreTransaction transaction);

	void commit(XmlDataStoreTransaction transaction, final XmlDatStoreCommittedResourceRecord record);

	void rollback(XmlDataStoreTransaction transaction);

	void release();

	void rollbackFailedCommit(XmlDataStoreTransaction transaction, Map<Object, Object> changes);

}
