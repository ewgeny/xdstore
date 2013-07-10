package org.flib.xdstore.serialization;

import java.io.Writer;
import java.util.Collection;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public interface IXmlDataStoreObjectsWriter {

	void writeReferences(Writer writer, Collection<IXmlDataStoreIdentifiable> references)
	        throws XmlDataStoreIOException;

	void writeObjects(Writer writer, Collection<IXmlDataStoreIdentifiable> objects) throws XmlDataStoreIOException;
}
