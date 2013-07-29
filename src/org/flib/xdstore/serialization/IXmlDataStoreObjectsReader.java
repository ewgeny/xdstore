package org.flib.xdstore.serialization;

import java.io.Reader;
import java.util.Collection;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStoreObjectIdField;

public interface IXmlDataStoreObjectsReader {

	Collection<IXmlDataStoreIdentifiable> readReferences(Reader reader) throws XmlDataStoreIOException;

	Collection<IXmlDataStoreIdentifiable> readObjects(Reader reader) throws XmlDataStoreIOException;

	Collection<Object> readAnnotatedReferences(Reader reader, XmlDataStoreObjectIdField field) throws XmlDataStoreIOException;

	Collection<Object> readAnnotatedObjects(Reader reader) throws XmlDataStoreIOException;
}
