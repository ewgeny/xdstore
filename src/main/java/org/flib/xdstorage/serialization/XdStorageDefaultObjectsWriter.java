package org.flib.xdstorage.serialization;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class XdStorageDefaultObjectsWriter implements IXdStorageObjectsWriter {

    private final XdStorageServicesLocator services;

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;

    private final IXdStorageIdGenerator idGenerator;

    private Map<Class<?>, Collection<XdStorageObjectField>> properties = new HashMap<>();

    public XdStorageDefaultObjectsWriter(final XdStorageServicesLocator services, final IXdStorageSimpleTypeHelper simpleTypeHelper, final IXdStorageIdGenerator idGenerator) {
        this.services = services;
        this.simpleTypeHelper = simpleTypeHelper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void writeReferences(final Writer writer, final XdStorageObjectIdField field, final Collection<Object> references) throws XdStorageIOException {
        try {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writeNewLevelAndTabulations(writer, 0);
            writer.write("<references>");
            for (final Object reference : references) {
                writeReference(reference, writer, field, 1);
            }
            writeNewLevelAndTabulations(writer, 0);
            writer.write("</references>");
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    @Override
    public void writeObjects(final Writer writer, final Collection<Object> objects) throws XdStorageIOException {
        try {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writeNewLevelAndTabulations(writer, 0);
            writer.write("<objects>");
            for (final Object object : objects) {
                writeObject(object, writer, 1);
            }
            writeNewLevelAndTabulations(writer, 0);
            writer.write("</objects>");
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
    }

    private void writeObject(final Object object, final Writer writer, int level) throws IOException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        writeObject(cl, policy, object, writer, level);
    }

    private void writeReference(final Object object, final Writer writer, final XdStorageObjectIdField field, final int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<reference class=\"");
        writer.append(XdStorageObjectUtils.getEntityClass(object.getClass()).getName());
        writer.append("\" classObjectId=\"");
        writer.append(field.get(object).getClass().getName());
        final Object value = field.get(object);
        final Class<?> cl = value.getClass();
        if (cl == Class.class) {
            writer.append("\" objectId=\"");
            writer.append(((Class)value).getName());
            writer.append("\"/>");
        } else if (XdStorageObjectUtils.isSimpleType(cl, null)) {
            writer.append("\" objectId=\"");
            writer.append(value.toString());
            writer.append("\"/>");
        } else {
            writer.append("\">");
            writeObject(field.getName(), cl, value, writer, level + 1);
            writeNewLevelAndTabulations(writer, level);
            writer.append("</reference>");
        }
    }

    @SuppressWarnings("unchecked")
    private void writeObject(final Class<?> cl, final XdStoragePolicy policy, final Object object, final Writer writer, int level) throws IOException {
        Collection<XdStorageObjectField> props = properties.get(cl);
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        if (props == null) {
            properties.put(cl, props = clInfo.getFields().values());
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("<object class=\"");
        writer.append(XdStorageObjectUtils.getEntityClass(object.getClass()).getName());
        writer.append("\">");

        for (final XdStorageObjectField property : props) {
            if (property.isIdField()) {
                final XdStorageObjectIdField idField = clInfo.getIdField();
                if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                        && idField.get(object) == null) {
                    try {
                        idField.set(XdStorageObserverService.getObservableWrapper(object), idGenerator.generate(cl, services.getStorage(), null));
                    } catch (final XdStorageException e) {
                        throw new XdStorageIOException(e);
                    }
                }
            }

            final Object value = property.get(object);
            if (value == null) {
                writeNull(property.getName(), writer, level + 1);
                continue;
            }

            final Class<?> c = value.getClass();
            if (c.isArray()) {
                writeArray(property.getName(), c, value, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, value)) {
                writeSimpleType(property.getName(), c, value, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(property.getName(), c, value, writer, level + 1);
            } else if (value instanceof Collection<?>) {
                writeCollection(property.getName(), c, (Collection<?>) value, writer, level + 1);
            } else if (value instanceof Map<?, ?>) {
                writeMap(property.getName(), c, (Map<?, ?>) value, writer, level + 1);
            } else if (property.isParent()) {
                writeReference(property.getName(), value, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(property.getName(), c, value, writer, level + 1);
            } else {
                writeObject(property.getName(), c, value, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</object>");
    }

    private void writeNull(final String name, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<object name=\"").append(name).append("\"/>");
    }

    private void writeNull(final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<object/>");
    }

    @SuppressWarnings("unchecked")
    private void writeArray(final String name, final Class<?> cl, final Object array, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<array name=\"").append(name).append("\" class=\"").append(cl.getComponentType().getName()).append("\" length=\"")
                .append(String.valueOf(Array.getLength(array))).append("\">");

        final int length = Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            final Object object = Array.get(array, i);
            final Class<?> c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 1);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 1);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 1);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 1);
            } else {
                writeObject(c, object, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</array>");
    }

    @SuppressWarnings("unchecked")
    private void writeArray(final Class<?> cl, final Object array, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<array class=\"").append(cl.getComponentType().getName()).append("\" length=\"").append(String.valueOf(Array.getLength(array))).append("\">");

        final int length = Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            final Object object = Array.get(array, i);
            final Class<?> c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 1);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 1);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 1);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 1);
            } else {
                writeObject(c, object, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</array>");
    }

    private void writeSimpleType(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<object name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\" value=\"")
                .append(encode(simpleTypeHelper.simpleTypeToString(object))).append("\"/>");
    }

    private void writeSimpleType(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<object class=\"").append(cl.getName()).append("\" value=\"")
                .append(encode(simpleTypeHelper.simpleTypeToString(object))).append("\"/>");
    }

    private void writeEnumValue(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<enum name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\" value=\"").append(encode(object.toString())).append("\"/>");
    }

    private void writeEnumValue(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<enum class=\"").append(cl.getName()).append("\" value=\"").append(encode(object.toString())).append("\"/>");
    }

    @SuppressWarnings("unchecked")
    private void writeCollection(final String name, final Class<?> cl, final Collection<?> collection, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<collection name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\">");
        for (final Object object : collection) {
            final Class<?> c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 1);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 1);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 1);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 1);
            } else {
                writeObject(c, object, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</collection>");
    }

    @SuppressWarnings("unchecked")
    private void writeCollection(final Class<?> cl, final Collection<?> collection, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<collection class=\"").append(cl.getName()).append("\">");
        for (final Object object : collection) {
            final Class<?> c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 1);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 1);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 1);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 1);
            } else {
                writeObject(c, object, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</collection>");
    }

    @SuppressWarnings("unchecked")
    private void writeMap(final String name, final Class<?> cl, final Map<?, ?> map, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<map name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\">");
        for (final Map.Entry<?, ?> pair : map.entrySet()) {
            writeNewLevelAndTabulations(writer, level + 1);
            writer.append("<entry>");
            final Object key = pair.getKey();
            Class<?> c = key != null ? key.getClass() : null;
            if (key == null) {
                writeNull(writer, level + 2);
            } else if (c.isArray()) {
                writeArray(c, key, writer, level + 2);
            } else if (simpleTypeHelper.isSimpleType(c, key)) {
                writeSimpleType(c, key, writer, level + 2);
            } else if (c.isEnum()) {
                writeEnumValue(c, key, writer, level + 2);
            } else if (key instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) key, writer, level + 2);
            } else if (key instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) key, writer, level + 2);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, key, writer, level + 2);
            } else {
                writeObject(c, key, writer, level + 2);
            }

            final Object object = pair.getValue();
            c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 2);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 2);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 2);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 2);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 2);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 2);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 2);
            } else {
                writeObject(c, object, writer, level + 2);
            }
            writeNewLevelAndTabulations(writer, level + 1);
            writer.append("</entry>");
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</map>");
    }

    @SuppressWarnings("unchecked")
    private void writeMap(final Class<?> cl, final Map<?, ?> map, final Writer writer, int level) throws IOException {
        writeNewLevelAndTabulations(writer, level);
        writer.append("<map class=\"").append(cl.getName()).append("\">");
        for (final Map.Entry<?, ?> pair : map.entrySet()) {
            writeNewLevelAndTabulations(writer, level + 1);
            writer.append("<entry>");
            final Object key = pair.getKey();
            Class<?> c = key != null ? key.getClass() : null;
            if (key == null) {
                writeNull(writer, level + 2);
            } else if (c.isArray()) {
                writeArray(c, key, writer, level + 2);
            } else if (simpleTypeHelper.isSimpleType(c, key)) {
                writeSimpleType(c, key, writer, level + 2);
            } else if (c.isEnum()) {
                writeEnumValue(c, key, writer, level + 2);
            } else if (key instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) key, writer, level + 2);
            } else if (key instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) key, writer, level + 2);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, key, writer, level + 2);
            } else {
                writeObject(c, key, writer, level + 2);
            }

            final Object object = pair.getValue();
            c = object != null ? object.getClass() : null;
            if (object == null) {
                writeNull(writer, level + 2);
            } else if (c.isArray()) {
                writeArray(c, object, writer, level + 2);
            } else if (simpleTypeHelper.isSimpleType(c, object)) {
                writeSimpleType(c, object, writer, level + 2);
            } else if (c.isEnum()) {
                writeEnumValue(c, object, writer, level + 2);
            } else if (object instanceof Collection<?>) {
                writeCollection(c, (Collection<?>) object, writer, level + 2);
            } else if (object instanceof Map<?, ?>) {
                writeMap(c, (Map<?, ?>) object, writer, level + 2);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(c, object, writer, level + 2);
            } else {
                writeObject(c, object, writer, level + 2);
            }
            writeNewLevelAndTabulations(writer, level + 1);
            writer.append("</entry>");
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</map>");
    }

    private void writeObject(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
            writeObjectData(name, cl, object, writer, level);
        } else {
            writeReference(name, object, writer, level);
        }
    }

    private void writeObject(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == null || policy == XdStoragePolicy.StoreWithParentObject) {
            writeObjectData(cl, object, writer, level);
        } else {
            writeReference(object, writer, clInfo.getIdField(), level);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeObjectData(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        Collection<XdStorageObjectField> props = properties.get(cl);
        if (props == null) {
            properties.put(cl, props = XdStorageObjectUtils.getClassInfo(cl).getFields().values());
        }

        writeNewLevelAndTabulations(writer, level);
        writer.append("<object name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\">");
        for (final XdStorageObjectField property : props) {
            if (property.isIdField()) {
                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
                final XdStorageObjectIdField idField = clInfo.getIdField();
                if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                        && idField.get(object) == null) {
                    try {
                        idField.set(XdStorageObserverService.getObservableWrapper(object), idGenerator.generate(cl, services.getStorage(), null));
                    } catch (final XdStorageException e) {
                        throw new XdStorageIOException(e);
                    }
                }
            }

            final Object value = property.get(object);
            final Class<?> c = value != null ? value.getClass() : null;
            if (value == null) {
                writeNull(property.getName(), writer, level + 1);
            } else if (c.isArray()) {
                writeArray(property.getName(), c, value, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, value)) {
                writeSimpleType(property.getName(), c, value, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(property.getName(), c, value, writer, level + 1);
            } else if (value instanceof Collection<?>) {
                writeCollection(property.getName(), c, (Collection<?>) value, writer, level + 1);
            } else if (value instanceof Map<?, ?>) {
                writeMap(property.getName(), c, (Map<?, ?>) value, writer, level + 1);
            } else if (property.isParent()) {
                writeReference(property.getName(), value, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(property.getName(), c, value, writer, level + 1);
            } else {
                writeObject(property.getName(), c, value, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</object>");
    }

    @SuppressWarnings("unchecked")
    private void writeObjectData(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
        Collection<XdStorageObjectField> props = properties.get(cl);
        if (props == null) {
            properties.put(cl, props = XdStorageObjectUtils.getClassInfo(cl).getFields().values());
        }

        writeNewLevelAndTabulations(writer, level);
        writer.append("<object class=\"").append(cl.getName()).append("\">");
        for (final XdStorageObjectField property : props) {
            if (property.isIdField()) {
                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
                final XdStorageObjectIdField idField = clInfo.getIdField();
                if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                        && idField.get(object) == null) {
                    try {
                        idField.set(XdStorageObserverService.getObservableWrapper(object), idGenerator.generate(cl, services.getStorage(), null));
                    } catch (final XdStorageException e) {
                        throw new XdStorageIOException(e);
                    }
                }
            }

            final Object value = property.get(object);
            final Class<?> c = value != null ? value.getClass() : null;
            if (value == null) {
                writeNull(property.getName(), writer, level + 1);
            } else if (c.isArray()) {
                writeArray(property.getName(), c, value, writer, level + 1);
            } else if (simpleTypeHelper.isSimpleType(c, value)) {
                writeSimpleType(property.getName(), c, value, writer, level + 1);
            } else if (c.isEnum()) {
                writeEnumValue(property.getName(), c, value, writer, level + 1);
            } else if (value instanceof Collection<?>) {
                writeCollection(property.getName(), c, (Collection<?>) value, writer, level + 1);
            } else if (value instanceof Map<?, ?>) {
                writeMap(property.getName(), c, (Map<?, ?>) value, writer, level + 1);
            } else if (property.isParent()) {
                writeReference(property.getName(), value, writer, level + 1);
            } else if (XdStorageObjectUtils.getClassInfo(c).getIdField() != null) {
                writeObject(property.getName(), c, value, writer, level + 1);
            } else {
                writeObject(property.getName(), c, value, writer, level + 1);
            }
        }
        writeNewLevelAndTabulations(writer, level);
        writer.append("</object>");
    }

    private void writeReference(final String name, final Object object, final Writer writer, int level) throws IOException {
        final XdStorageObjectIdField field = XdStorageObjectUtils.getClassInfo(object.getClass()).getIdField();
        writeNewLevelAndTabulations(writer, level);
        writer.append("<reference name=\"").append(name).append("\" class=\"");
        Class<?> entityClass = XdStorageObjectUtils.getEntityClass(object.getClass());
        writer.append(entityClass.getName());
        writer.append("\" classObjectId=\"");
        if (field.get(object) == null) {
            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(entityClass);
            final XdStorageObjectIdField idField = clInfo.getIdField();
            if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                    || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                    && idField.get(object) == null) {
                try {
                    idField.set(XdStorageObserverService.getObservableWrapper(object), idGenerator.generate(entityClass, services.getStorage(), null));
                } catch (final XdStorageException e) {
                    throw new XdStorageIOException(e);
                }
            }
        }
        writer.append(field.get(object).getClass().getName());
        final Object value = field.get(object);
        final Class<?> cl = value.getClass();
        if (cl == Class.class) {
            writer.append("\" objectId=\"");
            writer.append(((Class)value).getName());
            writer.append("\"/>");
        } else if (XdStorageObjectUtils.isSimpleType(cl, null)) {
            writer.append("\" objectId=\"");
            writer.append(value.toString());
            writer.append("\"/>");
        } else {
            writer.append("\">");
            writeObject(field.getName(), cl, value, writer, level + 1);
            writeNewLevelAndTabulations(writer, level);
            writer.append("</reference>");
        }
    }

    private String encode(final String value) {
        if (value == null) {
            return null;
        }
        boolean anyCharactersProtected = false;

        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            boolean controlCharacter = ch < 32;
            boolean unicodeButNotAscii = ch > 126;
            boolean characterWithSpecialMeaningInXML = ch == '<' || ch == '&' || ch == '>' || ch == '\"' || ch == '\'';

            if (characterWithSpecialMeaningInXML || unicodeButNotAscii || controlCharacter) {
                stringBuffer.append("&#" + (int) ch + ";");
                anyCharactersProtected = true;
            } else {
                stringBuffer.append(ch);
            }
        }

        return anyCharactersProtected ? stringBuffer.toString() : value;
    }

    private void writeNewLevelAndTabulations(final Writer writer, int level) throws IOException {
        writer.append("\r\n");
        for (int i = 0; i < level; ++i)
            writer.append('\t');
    }

}
