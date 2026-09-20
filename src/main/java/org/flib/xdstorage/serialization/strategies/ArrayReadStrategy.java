package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.serialization.XdStorageXmlHelper;
import org.flib.xdstorage.serialization.XdStoragePrimitiveMapper;
import java.lang.reflect.Array;
import java.util.Map;

public class ArrayReadStrategy implements XdStorageXmlReadStrategy {
    @Override public boolean supports(String name) { return "array".equals(name); }

    @Override
    public Object read(String[] fieldName, XMLStreamReader xmlReader, XdStorageXmlReaderContext context) throws Exception {
        Map<String, String> attrs = XdStorageXmlHelper.extractAttributes(xmlReader);
        fieldName[0] = attrs.get("name");
        String className = attrs.get("class");
        int length = Integer.parseInt(attrs.get("length"));

        Class<?> componentType = XdStoragePrimitiveMapper.getPrimitiveType(className);
        if (componentType == null) componentType = Class.forName(className);

        Object array = Array.newInstance(componentType, length);
        String[] fn = new String[]{null};
        int i = 0;

        while (xmlReader.hasNext()) {
            int type = xmlReader.nextTag();
            if (type == XMLStreamConstants.END_ELEMENT && "array".equals(xmlReader.getLocalName())) break;
            if (type != XMLStreamConstants.START_ELEMENT) continue;

            Array.set(array, i, context.readNextElement(xmlReader.getLocalName(), fn, xmlReader));
            i++;
        }
        return array;
    }
}
