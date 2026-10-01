package org.flib.xdstorage.serialization;

import javax.xml.stream.XMLStreamReader;

public class XdXmlStreamParser {
    public String getAttr(final XMLStreamReader reader, final String name) {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; ++i) {
            if (reader.getAttributeLocalName(i).equals(name)) return reader.getAttributeValue(i);
        }
        return null;
    }

    public void fillAttrs(final XMLStreamReader reader, final String[] fn, final String[] cl, final String[] val, final String[] cId) {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; ++i) {
            final String name = reader.getAttributeLocalName(i);
            final String value = reader.getAttributeValue(i);
            if (name.equals("name")) fn[0] = value;
            else if (name.equals("class")) cl[0] = value;
            else if (name.equals("value") || name.equals("length")) val[0] = value;
            else if (name.equals("classObjectId")) cId[0] = value;
            else if (name.equals("objectId") || name.equals("dataStorageId")) val[0] = value;
        }
    }
}
