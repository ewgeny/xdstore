package org.flib.xdstore.serialization;

import java.io.Reader;
import java.util.Collection;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public interface IXmlDataStoreObjectsReader {

	Collection<IXmlDataStoreIdentifiable> readReferences(Reader reader) throws XmlDataStoreIOException;

	Collection<IXmlDataStoreIdentifiable> readObjects(Reader reader) throws XmlDataStoreIOException;
}
