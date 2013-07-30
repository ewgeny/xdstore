package org.flib.xdstore.serialization;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStoreObjectIdField;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsWriter;
import org.flib.xdstore.serialization.XmlDataStoreClassProperty;
import org.flib.xdstore.serialization.XmlDataStoreIOException;
import org.flib.xdstore.utils.ObjectUtils;

public class XmlDataStoreDefaultObjectsWriter implements IXmlDataStoreObjectsWriter {

	private Map<Class<?>, XmlDataStorePolicy>             policies   = new HashMap<Class<?>, XmlDataStorePolicy>();

	private Map<Class<?>, Set<XmlDataStoreClassProperty>> properties = new HashMap<Class<?>, Set<XmlDataStoreClassProperty>>();

	public XmlDataStoreDefaultObjectsWriter(final Map<Class<?>, XmlDataStorePolicy> policies) {
		this.policies.putAll(policies);
	}

	@Override
	public void writeReferences(final Writer writer, final Collection<IXmlDataStoreIdentifiable> references) throws XmlDataStoreIOException {
		try {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeNewLevelAndTabulations(writer, 0);
			writer.write("<references>");
			for (final IXmlDataStoreIdentifiable reference : references) {
				writeReference(reference, writer, 1);
			}
			writeNewLevelAndTabulations(writer, 0);
			writer.write("</references>");
		} catch (final Throwable cause) {
			throw new XmlDataStoreIOException(cause);
		}
	}

	@Override
	public void writeAnnotatedReferences(final Writer writer, final XmlDataStoreObjectIdField field, final Collection<Object> references) throws XmlDataStoreIOException {
		try {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeNewLevelAndTabulations(writer, 0);
			writer.write("<references>");
			for (final Object reference : references) {
				writeAnnotatedReference(reference, writer, field, 1);
			}
			writeNewLevelAndTabulations(writer, 0);
			writer.write("</references>");
		} catch (final Throwable cause) {
			throw new XmlDataStoreIOException(cause);
		}
	}

	@Override
	public void writeObjects(final Writer writer, final Collection<IXmlDataStoreIdentifiable> objects) throws XmlDataStoreIOException {
		try {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeNewLevelAndTabulations(writer, 0);
			writer.write("<objects>");
			for (final IXmlDataStoreIdentifiable object : objects) {
				writeObject(object, writer, 1);
			}
			writeNewLevelAndTabulations(writer, 0);
			writer.write("</objects>");
		} catch (final Throwable cause) {
			throw new XmlDataStoreIOException(cause);
		}
	}

	@Override
	public void writeAnnotatedObjects(final Writer writer, final Collection<Object> objects) throws XmlDataStoreIOException {
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
			throw new XmlDataStoreIOException(cause);
		}
	}

	private void writeObject(final Object object, final Writer writer, int level) throws IOException {
		final Class<?> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		writeObject(cl, policy, object, writer, level);
	}

	private void writeReference(final IXmlDataStoreIdentifiable object, final Writer writer, int level) throws IOException {
		writeNewLevelAndTabulations(writer, level);
		writer.append("<reference class=\"");
		writer.append(object.getClass().getName());
		writer.append("\" dataStoreId=\"");
		writer.append(object.getDataStoreId());
		writer.append("\"/>");
	}

	private void writeAnnotatedReference(final Object object, final Writer writer, final XmlDataStoreObjectIdField field, final int level) throws IOException {
		writeNewLevelAndTabulations(writer, level);
		writer.append("<reference class=\"");
		writer.append(object.getClass().getName());
		writer.append("\" classObjectId=\"");
		writer.append(field.getObjectId(object).getClass().getName());
		writer.append("\" objectId=\"");
		writer.append(field.getObjectId(object).toString());
		writer.append("\"/>");
	}

	@SuppressWarnings("unchecked")
	private void writeObject(final Class<?> cl, final XmlDataStorePolicy policy, final Object object, final Writer writer, int level) throws IOException {
		Set<XmlDataStoreClassProperty> props = properties.get(cl);
		if (props == null) {
			properties.put(cl, props = ObjectUtils.getProperties(cl));
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("<object class=\"");
		writer.append(object.getClass().getName());
		writer.append("\">");

		for (final XmlDataStoreClassProperty property : props) {
			final Object value = property.get(object);
			if (value == null) {
				writeNull(property.name, writer, level + 1);
				continue;
			}

			final Class<?> c = value.getClass();
			if (c.isArray()) {
				writeArray(property.name, c, value, writer, level + 1);
			} else if (isSimpleType(c, value)) {
				writeSimpleType(property.name, c, value, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(property.name, c, value, writer, level + 1);
			} else if (value instanceof Collection<?>) {
				writeCollection(property.name, c, (Collection<?>) value, writer, level + 1);
			} else if (value instanceof Map<?, ?>) {
				writeMap(property.name, c, (Map<?, ?>) value, writer, level + 1);
			} else if (value instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject(property.name, (Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) value, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(property.name, c, value, writer, level + 1);
			} else {
				writeObject(property.name, c, value, writer, level + 1);
			}
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("</object>");
	}

	private boolean isSimpleType(final Class<?> cl, final Object object) {
		return object instanceof Number || cl == Boolean.class || cl == Character.class || cl == String.class;
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
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 1);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 1);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 1);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
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
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 1);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 1);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 1);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
			} else {
				writeObject(c, object, writer, level + 1);
			}
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("</array>");
	}

	private void writeSimpleType(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		writeNewLevelAndTabulations(writer, level);
		writer.append("<object name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\" value=\"").append(encode(object.toString())).append("\"/>");
	}

	private void writeSimpleType(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		writeNewLevelAndTabulations(writer, level);
		writer.append("<object class=\"").append(cl.getName()).append("\" value=\"").append(encode(object.toString())).append("\"/>");
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
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 1);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 1);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 1);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
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
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 1);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 1);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 1);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
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
			} else if (isSimpleType(c, key)) {
				writeSimpleType(c, key, writer, level + 2);
			} else if (c.isEnum()) {
				writeEnumValue(c, key, writer, level + 2);
			} else if (key instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) key, writer, level + 2);
			} else if (key instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) key, writer, level + 2);
			} else if (key instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) key, writer, level + 2);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, key, writer, level + 1);
			} else {
				writeObject(c, key, writer, level + 2);
			}

			final Object object = pair.getValue();
			c = object != null ? object.getClass() : null;
			if (object == null) {
				writeNull(writer, level + 2);
			} else if (c.isArray()) {
				writeArray(c, object, writer, level + 2);
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 2);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 2);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 2);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 2);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 2);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
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
			} else if (isSimpleType(c, key)) {
				writeSimpleType(c, key, writer, level + 2);
			} else if (c.isEnum()) {
				writeEnumValue(c, key, writer, level + 2);
			} else if (key instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) key, writer, level + 2);
			} else if (key instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) key, writer, level + 2);
			} else if (key instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) key, writer, level + 2);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, key, writer, level + 1);
			} else {
				writeObject(c, key, writer, level + 2);
			}

			final Object object = pair.getValue();
			c = object != null ? object.getClass() : null;
			if (object == null) {
				writeNull(writer, level + 2);
			} else if (c.isArray()) {
				writeArray(c, object, writer, level + 2);
			} else if (isSimpleType(c, object)) {
				writeSimpleType(c, object, writer, level + 2);
			} else if (c.isEnum()) {
				writeEnumValue(c, object, writer, level + 2);
			} else if (object instanceof Collection<?>) {
				writeCollection(c, (Collection<?>) object, writer, level + 2);
			} else if (object instanceof Map<?, ?>) {
				writeMap(c, (Map<?, ?>) object, writer, level + 2);
			} else if (object instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject((Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) object, writer, level + 2);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(c, object, writer, level + 1);
			} else {
				writeObject(c, object, writer, level + 2);
			}
			writeNewLevelAndTabulations(writer, level + 1);
			writer.append("</entry>");
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("</map>");
	}

	private void writeIdentifyableObject(final String name, final Class<? extends IXmlDataStoreIdentifiable> cl, final IXmlDataStoreIdentifiable object, final Writer writer,
	        int level) throws IOException {
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == null || policy == XmlDataStorePolicy.ParentObjectFile) {
			writeObject(name, cl, object, writer, level);
		} else {
			writeReference(name, object, writer, level);
		}
	}

	private void writeAnnotatedObject(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == null || policy == XmlDataStorePolicy.ParentObjectFile) {
			writeObject(name, cl, object, writer, level);
		} else {
			writeAnnotatedReference(name, object, writer, level);
		}
	}

	private void writeIdentifyableObject(final Class<? extends IXmlDataStoreIdentifiable> cl, final IXmlDataStoreIdentifiable object, final Writer writer, int level)
	        throws IOException {
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == null || policy == XmlDataStorePolicy.ParentObjectFile) {
			writeObject(cl, object, writer, level);
		} else {
			writeReference(object, writer, level);
		}
	}

	private void writeAnnotatedObject(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == null || policy == XmlDataStorePolicy.ParentObjectFile) {
			writeObject(cl, object, writer, level);
		} else {
			writeAnnotatedReference(object, writer, ObjectUtils.getAnnotatedObjectIdField(cl), level);
		}
	}

	@SuppressWarnings("unchecked")
	private void writeObject(final String name, final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		Set<XmlDataStoreClassProperty> props = properties.get(cl);
		if (props == null) {
			properties.put(cl, props = ObjectUtils.getProperties(cl));
		}

		writeNewLevelAndTabulations(writer, level);
		writer.append("<object name=\"").append(name).append("\" class=\"").append(cl.getName()).append("\">");
		for (final XmlDataStoreClassProperty property : props) {
			final Object value = property.get(object);
			final Class<?> c = value != null ? value.getClass() : null;
			if (value == null) {
				writeNull(property.name, writer, level + 1);
			} else if (c.isArray()) {
				writeArray(property.name, c, value, writer, level + 1);
			} else if (isSimpleType(c, value)) {
				writeSimpleType(property.name, c, value, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(property.name, c, value, writer, level + 1);
			} else if (value instanceof Collection<?>) {
				writeCollection(property.name, c, (Collection<?>) value, writer, level + 1);
			} else if (value instanceof Map<?, ?>) {
				writeMap(property.name, c, (Map<?, ?>) value, writer, level + 1);
			} else if (value instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject(property.name, (Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) value, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(property.name, c, value, writer, level + 1);
			} else {
				writeObject(property.name, c, value, writer, level + 1);
			}
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("</object>");
	}

	@SuppressWarnings("unchecked")
	private void writeObject(final Class<?> cl, final Object object, final Writer writer, int level) throws IOException {
		Set<XmlDataStoreClassProperty> props = properties.get(cl);
		if (props == null) {
			properties.put(cl, props = ObjectUtils.getProperties(cl));
		}

		writeNewLevelAndTabulations(writer, level);
		writer.append("<object class=\"").append(cl.getName()).append("\">");
		for (final XmlDataStoreClassProperty property : props) {
			final Object value = property.get(object);
			final Class<?> c = value != null ? value.getClass() : null;
			if (value == null) {
				writeNull(property.name, writer, level + 1);
			} else if (c.isArray()) {
				writeArray(property.name, c, value, writer, level + 1);
			} else if (isSimpleType(c, value)) {
				writeSimpleType(property.name, c, value, writer, level + 1);
			} else if (c.isEnum()) {
				writeEnumValue(property.name, c, value, writer, level + 1);
			} else if (value instanceof Collection<?>) {
				writeCollection(property.name, c, (Collection<?>) value, writer, level + 1);
			} else if (value instanceof Map<?, ?>) {
				writeMap(property.name, c, (Map<?, ?>) value, writer, level + 1);
			} else if (value instanceof IXmlDataStoreIdentifiable) {
				writeIdentifyableObject(property.name, (Class<? extends IXmlDataStoreIdentifiable>) c, (IXmlDataStoreIdentifiable) value, writer, level + 1);
			} else if (ObjectUtils.getAnnotatedObjectIdField(c) != null) {
				writeAnnotatedObject(property.name, c, value, writer, level + 1);
			} else {
				writeObject(property.name, c, value, writer, level + 1);
			}
		}
		writeNewLevelAndTabulations(writer, level);
		writer.append("</object>");
	}

	private void writeReference(final String name, final IXmlDataStoreIdentifiable object, final Writer writer, int level) throws IOException {
		writeNewLevelAndTabulations(writer, level);
		writer.append("<reference name=\"").append(name).append("\" class=\"");
		writer.append(object.getClass().getName());
		writer.append("\" dataStoreId=\"");
		writer.append(object.getDataStoreId());
		writer.append("\"/>");
	}

	private void writeAnnotatedReference(final String name, final Object object, final Writer writer, int level) throws IOException {
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(object.getClass());
		writeNewLevelAndTabulations(writer, level);
		writer.append("<reference name=\"").append(name).append("\" class=\"");
		writer.append(object.getClass().getName());
		writer.append("\" classObjectId=\"");
		writer.append(field.getObjectId(object).getClass().getName());
		writer.append("\" objectId=\"");
		writer.append(field.getObjectId(object).toString());
		writer.append("\"/>");
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
