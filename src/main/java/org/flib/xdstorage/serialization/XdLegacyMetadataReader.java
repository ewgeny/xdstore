package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.*;

public class XdLegacyMetadataReader {
    private final XdPrimitiveTypeMapper mapper;
    private final XdObjectPropertyHydrator hydrator;
    private final XdXmlStreamParser xml;

    public XdLegacyMetadataReader(XdPrimitiveTypeMapper mapper, XdObjectPropertyHydrator hydrator, XdXmlStreamParser xml) {
        this.mapper = mapper;
        this.hydrator = hydrator;
        this.xml = xml;
    }

    public Collection<XdStorageIdentifiableObject> readData(final Reader r, final XdStorageObjectIdField f) throws XdStorageIOException {
        Collection<XdStorageIdentifiableObject> res = new ArrayList<>();
        try {
            XMLStreamReader xReader = XMLInputFactory.newInstance().createXMLStreamReader(r);
            final String[] fn = new String[]{null};
            while (xReader.hasNext()) {
                if (xReader.next() == XMLStreamConstants.START_ELEMENT) {
                    Object tmp = null;
                    if (xReader.getLocalName().equals("object")) tmp = readObjectData(fn, xReader);
                    else if (xReader.getLocalName().equals("reference")) tmp = readObjectDataReference(fn, xReader);
                    if (tmp != null) res.add((XdStorageIdentifiableObject) tmp);
                }
            }
        } catch (Throwable cause) { throw new XdStorageIOException(cause); }
        return res;
    }

    private Object readObjectData(final String[] fn, final XMLStreamReader xReader) throws Exception {
        final String[] cl = {null}, val = {null}, cId = {null};
        xml.fillAttrs(xReader, fn, cl, val, cId);
        if (cl[0] == null) { xReader.nextTag(); return null; }

        final Class<?> targetClass = Class.forName(cl[0]);
        if (val[0] != null) { xReader.nextTag(); return mapper.toSimple(targetClass, val[0]); }

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(targetClass);
        final XdStorageIdentifiableObject res = new XdStorageIdentifiableObject();
        res.setType(targetClass);

        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("object")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT) continue;

            final String tag = xReader.getLocalName();
            Object tmp = null;
            if (tag.equals("object")) tmp = readObjectData(subFn, xReader);
            else if (tag.equals("array")) tmp = readObjectDataArray(subFn, xReader);
            else if (tag.equals("primitive")) tmp = mapper.toPrimitive(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
            else if (tag.equals("enum")) tmp = mapper.toEnum(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
            else if (tag.equals("collection")) tmp = readObjectDataCollection(subFn, xReader);
            else if (tag.equals("map")) tmp = readObjectDataMap(subFn, xReader);
            else if (tag.equals("reference")) tmp = readObjectDataReference(subFn, xReader);

            if (subFn[0].equals(clInfo.getIdField().getName())) res.setId(tmp);
            else res.setProperty(subFn[0], tmp);
        }
        return res;
    }

    private Object readObjectDataArray(final String[] fn, final XMLStreamReader xReader) throws Exception {
        final String[] cl = {null}, len = {null}, dummy = {null};
        xml.fillAttrs(xReader, fn, cl, len, dummy);
        Class<?> compType = mapper.getPrimitiveType(cl[0]);
        if (compType == null) compType = Class.forName(cl[0]);
        final Object arr = Array.newInstance(compType, Integer.parseInt(len[0]));
        final String subFn[] = new String[]{null};
        int i = 0;
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("array")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT) continue;
            Object tmp = parseSub(xReader, subFn);
            Array.set(arr, i++, tmp);
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private Object readObjectDataCollection(final String[] fn, final XMLStreamReader xReader) throws Exception {
        final String cl = xml.getAttr(xReader, "class");
        fn[0] = xml.getAttr(xReader, "name");
        Collection<Object> col = hydrator.createCol(cl);
        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("collection")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT) continue;
            col.add(parseSub(xReader, subFn));
        }
        return col;
    }

    private Object readObjectDataMap(final String[] fn, final XMLStreamReader xReader) throws Exception {
        final String cl = xml.getAttr(xReader, "class");
        fn[0] = xml.getAttr(xReader, "name");
        Map<Object, Object> map = hydrator.createMap(cl);
        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("map")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT || !xReader.getLocalName().equals("entry")) continue;
            xReader.nextTag(); Object k = parseSub(xReader, subFn);
            xReader.nextTag(); Object v = parseSub(xReader, subFn);
            map.put(k, v);
        }
        return map;
    }

    private Object parseSub(XMLStreamReader xReader, String[] subFn) throws Exception {
        final String tag = xReader.getLocalName();
        if (tag.equals("object")) return readObjectData(subFn, xReader);
        if (tag.equals("array")) return readObjectDataArray(subFn, xReader);
        if (tag.equals("primitive")) return mapper.toPrimitive(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
        if (tag.equals("enum")) return mapper.toEnum(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
        if (tag.equals("collection")) return readObjectDataCollection(subFn, xReader);
        if (tag.equals("map")) return readObjectDataMap(subFn, xReader);
        if (tag.equals("reference")) return readObjectDataReference(subFn, xReader);
        return null;
    }

    private Object readObjectDataReference(final String fn[], final XMLStreamReader xReader) throws Exception {
        final String[] cl = {null}, val = {null}, cId = {null};
        xml.fillAttrs(xReader, fn, cl, val, cId);
        final Class<?> idClass = Class.forName(cId[0]);
        final Constructor<?> c = idClass.getConstructor(String.class);
        final Object objectId = c.newInstance(val[0]);
        final XdStorageIdentifiableObject res = new XdStorageIdentifiableObject();
        res.setType(Class.forName(cl[0]));
        res.setId(objectId);
        return res;
    }
}
