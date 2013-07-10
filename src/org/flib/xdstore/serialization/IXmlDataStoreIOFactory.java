package org.flib.xdstore.serialization;

import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStorePolicy;

public interface IXmlDataStoreIOFactory {

	IXmlDataStoreObjectsReader newInstanceReader(
	        Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies);

	IXmlDataStoreObjectsWriter newInstanceWriter(
	        Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies);

}
