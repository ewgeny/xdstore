package org.flib.xdstore.serialization;

import java.util.Map;

import org.flib.xdstore.XmlDataStorePolicy;

public class XmlDataStoreDefaultIOFactory implements IXmlDataStoreIOFactory {

	@Override
	public IXmlDataStoreObjectsReader newInstanceReader(final Map<Class<?>, XmlDataStorePolicy> policies) {
		return new XmlDataStoreDefaultObjectsReader(policies);
	}

	@Override
	public IXmlDataStoreObjectsWriter newInstanceWriter(final Map<Class<?>, XmlDataStorePolicy> policies) {
		return new XmlDataStoreDefaultObjectsWriter(policies);
	}

}
