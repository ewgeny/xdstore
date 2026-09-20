package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.utils.XdStorageObjectField;
import java.util.Map;

public interface XdStorageXmlReaderContext {

    IXdStorageSimpleTypeHelper getSimpleTypeHelper();

    Object readNextElement(String elementName, String[] fieldName, XMLStreamReader xmlReader) throws Exception;

    Map<String, XdStorageObjectField> getFieldsCache(Class<?> clazz);
}
