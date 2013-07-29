package org.flib.xdstore.serialization;

import java.util.Map;

import org.flib.xdstore.XmlDataStorePolicy;

public interface IXmlDataStoreIOFactory {

	IXmlDataStoreObjectsReader newInstanceReader(Map<Class<?>, XmlDataStorePolicy> policies);

	IXmlDataStoreObjectsWriter newInstanceWriter(Map<Class<?>, XmlDataStorePolicy> policies);

}
