package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.*;

public class XdStorageDefaultObjectsReader implements IXdStorageObjectsReader {

    private final XdPrimitiveTypeMapper mapper;
    private final XdObjectPropertyHydrator hydrator;
    private final XdXmlStreamParser xml;
    private final XdLegacyMetadataReader legacyReader;

    public XdStorageDefaultObjectsReader(final IXdStorageSimpleTypeHelper simpleHelper) {
        this.mapper = new XdPrimitiveTypeMapper(simpleHelper);
        this.hydrator = new XdObjectPropertyHydrator();
        this.xml = new XdXmlStreamParser();
        this.legacyReader = new XdLegacyMetadataReader(mapper, hydrator, xml);
    }

    @Override
    public Collection<Object> readReferences(final Reader r, final XdStorageObjectIdField f) throws XdStorageIOException {
        Collection<Object> res = new ArrayList<>();
        try {
            XMLStreamReader xReader = XMLInputFactory.newInstance().createXMLStreamReader(r);
            final String[] fn = new String[]{null};
            while (xReader.hasNext()) {
                if (xReader.next() == XMLStreamConstants.START_ELEMENT && (xReader.getLocalName().equals("object") || xReader.getLocalName().equals("reference"))) {
                    Object tmp = readReference(fn, xReader, f, null);
                    if (tmp != null) res.add(tmp);
                }
            }
        } catch (Throwable cause) {
            throw new XdStorageIOException(cause);
        }
        return res;
    }

    @Override
    public Collection<Object> read(final Reader r) throws XdStorageIOException {
        return read(r, null);
    }

    @Override
    public Collection<Object> read(final Reader r, final IXdStorageTransaction tx) throws XdStorageIOException {
        Collection<Object> res = new ArrayList<>();
        try {
            XMLStreamReader xReader = XMLInputFactory.newInstance().createXMLStreamReader(r);
            while (xReader.hasNext()) {
                if (xReader.next() == XMLStreamConstants.START_ELEMENT && (xReader.getLocalName().equals("object") || xReader.getLocalName().equals("reference"))) {
                    final String[] fn = new String[]{null};
                    Object tmp = xReader.getLocalName().equals("object") ? read(fn, xReader, tx) : readReference(fn, xReader, tx);
                    if (tmp != null) res.add(tmp);
                }
            }
        } catch (Throwable cause) {
            if (cause.getMessage() != null && cause.getMessage().contains("Premature end of file."))
                return Collections.emptyList();
            throw new XdStorageIOException(cause);
        }
        return res;
    }

    private Object read(final String[] fn, final XMLStreamReader xReader, final IXdStorageTransaction tx) throws Exception {
        final String[] cl = {null}, val = {null}, cId = {null};
        xml.fillAttrs(xReader, fn, cl, val, cId);
        if (cl[0] == null) {
            xReader.nextTag();
            return null;
        }

        final Class<?> targetClass = Class.forName(cl[0]);
        if (val[0] != null) {
            xReader.nextTag();
            return mapper.toSimple(targetClass, val[0]);
        }

        Object res = hydrator.create(targetClass);
        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("object")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT) continue;

            final String tag = xReader.getLocalName();
            Object tmp = null;
            if (tag.equals("object")) tmp = read(subFn, xReader, tx);
            else if (tag.equals("array")) tmp = readArray(subFn, xReader, tx);
            else if (tag.equals("primitive"))
                tmp = mapper.toPrimitive(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
            else if (tag.equals("enum"))
                tmp = mapper.toEnum(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
            else if (tag.equals("collection")) tmp = readCollection(subFn, xReader, tx);
            else if (tag.equals("map")) tmp = readMap(subFn, xReader, tx);
            else if (tag.equals("reference")) tmp = readReference(subFn, xReader, tx);

            hydrator.inject(res, hydrator.getMeta(targetClass), subFn[0], tmp);
        }
        return res;
    }

    private Object readArray(final String[] fn, final XMLStreamReader xReader, final IXdStorageTransaction tx) throws Exception {
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
            Array.set(arr, i++, parseDomainSub(xReader, subFn, tx));
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private Object readCollection(final String[] fn, final XMLStreamReader xReader, final IXdStorageTransaction tx) throws Exception {
        final String cl = xml.getAttr(xReader, "class");
        fn[0] = xml.getAttr(xReader, "name");
        Collection<Object> col = hydrator.createCol(cl);
        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("collection"))
                break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT) continue;
            col.add(parseDomainSub(xReader, subFn, tx));
        }
        return col;
    }

    private Object readMap(final String[] fn, final XMLStreamReader xReader, final IXdStorageTransaction tx) throws Exception {
        fn[0] = xml.getAttr(xReader, "name");
        final String cl = xml.getAttr(xReader, "class");
        Map<Object, Object> map = hydrator.createMap(cl);
        final String subFn[] = new String[]{null};
        while (xReader.hasNext()) {
            if (xReader.nextTag() == XMLStreamConstants.END_ELEMENT && xReader.getLocalName().equals("map")) break;
            if (xReader.getEventType() != XMLStreamConstants.START_ELEMENT || !xReader.getLocalName().equals("entry"))
                continue;
            xReader.nextTag();
            Object k = parseDomainSub(xReader, subFn, tx);
            xReader.next();
            xReader.nextTag();
            Object v = parseDomainSub(xReader, subFn, tx);
            map.put(k, v);
        }
        return map;
    }

    private Object parseDomainSub(XMLStreamReader xReader, String[] subFn, IXdStorageTransaction tx) throws Exception {
        final String tag = xReader.getLocalName();
        if (tag.equals("object")) return read(subFn, xReader, tx);
        if (tag.equals("array")) return readArray(subFn, xReader, tx);
        if (tag.equals("enum")) return mapper.toEnum(xml.getAttr(xReader, "class"), xml.getAttr(xReader, "value"));
        if (tag.equals("collection")) return readCollection(subFn, xReader, tx);
        if (tag.equals("map")) return readMap(subFn, xReader, tx);
        if (tag.equals("reference")) return readReference(subFn, xReader, tx);
        return null;
    }

    private Object readReference(final String[] fn, final XMLStreamReader xReader, final IXdStorageTransaction tx) throws Exception {
        final String[] cl = {null}, val = {null}, cId = {null};
        xml.fillAttrs(xReader, fn, cl, val, cId);
        final Class<?> targetClass = Class.forName(cl[0]);
        Object objectId = extractId(xReader, cId[0], val[0], tx);

        if (tx != null && objectId != null) {
            Object cached = tx.getStorage().load(targetClass, objectId);
            if (cached != null) return cached;
        }

        final Object res = hydrator.create(targetClass);
        XdStorageObjectUtils.getClassInfo(res.getClass()).getIdField().set(res, objectId);
        return res;
    }

    private Object readReference(final String[] fn, final XMLStreamReader xReader, final XdStorageObjectIdField field, final IXdStorageTransaction tx) throws Exception {
        final String[] cl = {null}, val = {null}, cId = {null};
        xml.fillAttrs(xReader, fn, cl, val, cId);
        final Class<?> targetClass = Class.forName(cl[0]);
        Object objectId = extractId(xReader, cId[0], val[0], tx);

        if (tx != null && objectId != null) {
            Object cached = tx.getStorage().load(targetClass, objectId);
            if (cached != null) return cached;
        }

        final Object res = hydrator.create(targetClass);
        field.set(res, objectId);
        return res;
    }

    private Object extractId(XMLStreamReader xReader, String cId, String val, IXdStorageTransaction tx) throws Exception {
        final Class<?> idClass = Class.forName(cId);
        if (idClass == Class.class || XdStorageObjectUtils.isSimpleType(idClass, null)) {
            if (idClass == Class.class) return Class.forName(val);
            return idClass.getConstructor(String.class).newInstance(val);
        }
        while (xReader.hasNext()) {
            if (xReader.next() == XMLStreamConstants.START_ELEMENT && xReader.getLocalName().equals("object")) {
                final String[] fn = {null};
                return read(fn, xReader, tx);
            }
        }
        return null;
    }

    @Override
    public Collection readData(final Reader r, final XdStorageObjectIdField f) throws XdStorageIOException {
        return legacyReader.readData(r, f);
    }
}