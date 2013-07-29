package org.flib.xdstore.serialization;

import java.io.Writer;
import java.util.Collection;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStoreObjectIdField;

public interface IXmlDataStoreObjectsWriter {

	void writeReferences(Writer writer, Collection<IXmlDataStoreIdentifiable> references) throws XmlDataStoreIOException;

	void writeObjects(Writer writer, Collection<IXmlDataStoreIdentifiable> objects) throws XmlDataStoreIOException;

	void writeAnnotatedReferences(Writer writer, XmlDataStoreObjectIdField field, Collection<Object> references) throws XmlDataStoreIOException;

	void writeAnnotatedObjects(Writer writer, Collection<Object> objects) throws XmlDataStoreIOException;
}
