package org.flib.xdstore.serialization;

import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsReader;
import org.flib.xdstore.serialization.XmlDataStoreClassProperty;
import org.flib.xdstore.serialization.XmlDataStoreIOException;
import org.flib.xdstore.utils.ObjectUtils;

public class XmlDataStoreDefaultObjectsReader implements IXmlDataStoreObjectsReader {

	private Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies   = new HashMap<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy>();

	private Map<Class<?>, Map<String, XmlDataStoreClassProperty>>               properties = new HashMap<Class<?>, Map<String, XmlDataStoreClassProperty>>();

	public XmlDataStoreDefaultObjectsReader(final Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies) {
		this.policies.putAll(policies);
	}

	@Override
	public Collection<IXmlDataStoreIdentifiable> readReferences(final Reader reader) throws XmlDataStoreIOException {
		Collection<IXmlDataStoreIdentifiable> result = new ArrayList<IXmlDataStoreIdentifiable>();
		try {
			XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(reader);
			IXmlDataStoreIdentifiable tmp = null;
			while (xmlReader.hasNext()) {
				switch (xmlReader.next()) {
					case XMLStreamConstants.START_ELEMENT:
						if (xmlReader.getLocalName().equals("object") || xmlReader.getLocalName().equals("reference")) {
							tmp = readElement(xmlReader);
						}
						break;
				}
				if (tmp != null)
					result.add(tmp);
			}
		} catch (final Throwable cause) { // stupid quick solution
			throw new XmlDataStoreIOException(cause);
		}
		return result;
	}

	@Override
	public Collection<IXmlDataStoreIdentifiable> readObjects(final Reader reader) throws XmlDataStoreIOException {
		Collection<IXmlDataStoreIdentifiable> result = new ArrayList<IXmlDataStoreIdentifiable>();
		try {
			XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(reader);
			IXmlDataStoreIdentifiable tmp = null;
			while (xmlReader.hasNext()) {
				switch (xmlReader.next()) {
					case XMLStreamConstants.START_ELEMENT:
						if (xmlReader.getLocalName().equals("object") || xmlReader.getLocalName().equals("reference")) {
							tmp = readElement(xmlReader);
						}
						break;
				}
				if (tmp != null)
					result.add(tmp);
			}
		} catch (final Throwable cause) { // stupid quick solution
			throw new XmlDataStoreIOException(cause);
		}
		return result;
	}

	private IXmlDataStoreIdentifiable readElement(final XMLStreamReader xmlReader) throws InstantiationException,
	        IllegalAccessException, ClassNotFoundException, NoSuchMethodException, SecurityException,
	        IllegalArgumentException, InvocationTargetException, XMLStreamException {
		IXmlDataStoreIdentifiable result = null;
		final String[] fn = new String[] { null };
		final String name = xmlReader.getLocalName();
		if (name.equals("object")) {
			result = (IXmlDataStoreIdentifiable) readObject(fn, xmlReader);
		} else if (name.equals("reference")) {
			result = readReference(fn, xmlReader);
		}
		return result;
	}

	private Object readObject(final String fieldName[], final XMLStreamReader xmlReader) throws ClassNotFoundException,
	        InstantiationException, IllegalAccessException, NoSuchMethodException, SecurityException,
	        IllegalArgumentException, InvocationTargetException, XMLStreamException {
		final int count = xmlReader.getAttributeCount();
		String className = null, value = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			} else if (attName.equals("value")) {
				value = attValue;
			}
		}
		if(className == null) return null;

		final Class<?> cl = Class.forName(className);
		if (value != null) {
			xmlReader.nextTag();
			return buildSimpleObject(cl, value);
		}

		Map<String, XmlDataStoreClassProperty> props = properties.get(cl);
		if (props == null) {
			properties.put(cl, props = ObjectUtils.getPropertiesMap(cl));
		}

		Object result = cl.newInstance(), tmp = null;
		final String fn[] = new String[] { null };
		while (xmlReader.hasNext()) {
			final int type = xmlReader.nextTag();
			if (type == XMLStreamConstants.END_ELEMENT && xmlReader.getLocalName().equals("object"))
				break;
			if (type != XMLStreamConstants.START_ELEMENT)
				continue;

			final String elemName = xmlReader.getLocalName();
			if (elemName.equals("object")) {
				tmp = readObject(fn, xmlReader);
			} else if (elemName.equals("array")) {
				tmp = readArray(fn, xmlReader);
			} else if (elemName.equals("primitive")) {
				tmp = readPrimitive(fn, xmlReader);
			} else if (elemName.equals("enum")) {
				tmp = readEnum(fn, xmlReader);
			} else if (elemName.equals("collection")) {
				tmp = readCollection(fn, xmlReader);
			} else if (elemName.equals("map")) {
				tmp = readMap(fn, xmlReader);
			} else if (elemName.equals("reference")) {
				tmp = readReference(fn, xmlReader);
			}
			props.get(fn[0]).set(result, tmp);
		}

		return result;
	}

	private Object buildSimpleObject(final Class<?> cl, final String value) throws NoSuchMethodException,
	        SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException,
	        InvocationTargetException {
		if (cl == Boolean.class)
			return Boolean.valueOf(value);
		if (cl == Character.class)
			return Character.valueOf(value.charAt(0));
		if (cl == String.class)
			return value;
		final Constructor<?> c = cl.getConstructor(String.class);
		return c.newInstance(value);
	}

	private Object readArray(final String[] fieldName, final XMLStreamReader xmlReader) throws ClassNotFoundException,
	        InstantiationException, IllegalAccessException, XMLStreamException, NoSuchMethodException,
	        SecurityException, IllegalArgumentException, InvocationTargetException {
		final int count = xmlReader.getAttributeCount();
		String className = null, length = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			} else if (attName.equals("length")) {
				length = attValue;
			}
		}

		Class<?> componentType;
		if ((componentType = getPrimitiveType(className)) == null) {
			componentType = Class.forName(className);
		}
		final Object array = Array.newInstance(componentType, Integer.parseInt(length));

		Object tmp = null;
		final String fn[] = new String[] { null };
		int i = 0;
		while (xmlReader.hasNext()) {
			final int type = xmlReader.nextTag();
			if (type == XMLStreamConstants.END_ELEMENT && xmlReader.getLocalName().equals("array"))
				break;
			if (type != XMLStreamConstants.START_ELEMENT)
				continue;

			final String elemName = xmlReader.getLocalName();
			if (elemName.equals("object")) {
				tmp = readObject(fn, xmlReader);
			} else if (elemName.equals("array")) {
				tmp = readArray(fn, xmlReader);
			} else if (elemName.equals("primitive")) {
				tmp = readPrimitive(fn, xmlReader);
			} else if (elemName.equals("enum")) {
				tmp = readEnum(fn, xmlReader);
			} else if (elemName.equals("collection")) {
				tmp = readCollection(fn, xmlReader);
			} else if (elemName.equals("map")) {
				tmp = readMap(fn, xmlReader);
			} else if (elemName.equals("reference")) {
				tmp = readReference(fn, xmlReader);
			}
			Array.set(array, i, tmp);
			++i;
		}

		return array;
	}

	private Class<?> getPrimitiveType(final String className) {
		if (className.equals(byte.class.getName())) {
			return byte.class;
		}
		if (className.equals(short.class.getName())) {
			return short.class;
		}
		if (className.equals(int.class.getName())) {
			return int.class;
		}
		if (className.equals(long.class.getName())) {
			return long.class;
		}
		if (className.equals(float.class.getName())) {
			return float.class;
		}
		if (className.equals(double.class.getName())) {
			return double.class;
		}
		if (className.equals(boolean.class.getName())) {
			return boolean.class;
		}
		if (className.equals(char.class.getName())) {
			return char.class;
		}
		return null;
	}

	private Object readPrimitive(final String[] fieldName, final XMLStreamReader xmlReader) {
		final int count = xmlReader.getAttributeCount();
		String className = null, value = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			} else if (attName.equals("value")) {
				value = attValue;
			}
		}

		Object result = null;
		if (className.equals("byte")) {
			result = Byte.parseByte(value);
		} else if (className.equals("short")) {
			result = Short.parseShort(value);
		} else if (className.equals("int")) {
			result = Integer.parseInt(value);
		} else if (className.equals("long")) {
			result = Long.parseLong(value);
		} else if (className.equals("float")) {
			result = Float.parseFloat(value);
		} else if (className.equals("double")) {
			result = Double.parseDouble(value);
		} else if (className.equals("boolean")) {
			result = Boolean.parseBoolean(value);
		} else if (className.equals("char")) {
			result = value.charAt(0);
		}
		return result;
	}

	private Object readEnum(final String[] fieldName, final XMLStreamReader xmlReader) throws ClassNotFoundException,
	        NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
	        InvocationTargetException {
		final int count = xmlReader.getAttributeCount();
		String className = null, value = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			} else if (attName.equals("value")) {
				value = attValue;
			}
		}

		final Class<?> cl = Class.forName(className);
		final Method m = cl.getMethod("valueOf", String.class);
		return m.invoke(cl, value);
	}

	@SuppressWarnings("unchecked")
	private Object readCollection(final String[] fieldName, final XMLStreamReader xmlReader)
	        throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLStreamException,
	        NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException {
		final int count = xmlReader.getAttributeCount();
		String className = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			}
		}

		Class<?> cl = Class.forName(className);
		Collection<Object> collection = (Collection<Object>) cl.newInstance();
		final String[] fn = new String[] { null };
		while (xmlReader.hasNext()) {
			final int type = xmlReader.nextTag();
			if (type == XMLStreamConstants.END_ELEMENT && xmlReader.getLocalName().equals("collection"))
				break;
			if (type != XMLStreamConstants.START_ELEMENT)
				continue;

			final String elemName = xmlReader.getLocalName();
			Object tmp = null;
			if (elemName.equals("object")) {
				tmp = readObject(fn, xmlReader);
			} else if (elemName.equals("array")) {
				tmp = readArray(fn, xmlReader);
			} else if (elemName.equals("enum")) {
				tmp = readEnum(fn, xmlReader);
			} else if (elemName.equals("collection")) {
				tmp = readCollection(fn, xmlReader);
			} else if (elemName.equals("map")) {
				tmp = readMap(fn, xmlReader);
			} else if (elemName.equals("reference")) {
				tmp = readReference(fn, xmlReader);
			}
			collection.add(tmp);
		}
		return collection;
	}

	@SuppressWarnings("unchecked")
	private Object readMap(final String[] fieldName, final XMLStreamReader xmlReader) throws InstantiationException,
	        IllegalAccessException, ClassNotFoundException, XMLStreamException, NoSuchMethodException,
	        SecurityException, IllegalArgumentException, InvocationTargetException {
		final int count = xmlReader.getAttributeCount();
		String className = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);

			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			}
		}

		Class<?> cl = Class.forName(className);
		Map<Object, Object> map = (Map<Object, Object>) cl.newInstance();
		final String[] fn = new String[] { null };
		while (xmlReader.hasNext()) {
			final int type = xmlReader.nextTag();
			if (type == XMLStreamConstants.END_ELEMENT && xmlReader.getLocalName().equals("map"))
				break;
			if (type != XMLStreamConstants.START_ELEMENT || !xmlReader.getLocalName().equals("entry"))
				continue;

			xmlReader.nextTag();
			String elemName = xmlReader.getLocalName();
			Object key = null;
			if (elemName.equals("object")) {
				key = readObject(fn, xmlReader);
			} else if (elemName.equals("array")) {
				key = readArray(fn, xmlReader);
			} else if (elemName.equals("enum")) {
				key = readEnum(fn, xmlReader);
			} else if (elemName.equals("collection")) {
				key = readCollection(fn, xmlReader);
			} else if (elemName.equals("map")) {
				key = readMap(fn, xmlReader);
			} else if (elemName.equals("reference")) {
				key = readReference(fn, xmlReader);
			}

			xmlReader.nextTag();
			elemName = xmlReader.getLocalName();
			Object value = null;
			if (elemName.equals("object")) {
				value = readObject(fn, xmlReader);
			} else if (elemName.equals("array")) {
				value = readArray(fn, xmlReader);
			} else if (elemName.equals("enum")) {
				value = readEnum(fn, xmlReader);
			} else if (elemName.equals("collection")) {
				value = readCollection(fn, xmlReader);
			} else if (elemName.equals("map")) {
				value = readMap(fn, xmlReader);
			} else if (elemName.equals("reference")) {
				value = readReference(fn, xmlReader);
			}
			map.put(key, value);
		}
		return map;
	}

	private IXmlDataStoreIdentifiable readReference(final String fieldName[], final XMLStreamReader xmlReader)
	        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final int count = xmlReader.getAttributeCount();
		String className = null, idValue = null;
		for (int i = 0; i < count; ++i) {
			final String attName = xmlReader.getAttributeLocalName(i);
			final String attValue = xmlReader.getAttributeValue(i);
			if (attName.equals("name")) {
				fieldName[0] = attValue;
			} else if (attName.equals("class")) {
				className = attValue;
			} else if (attName.equals("dataStoreId")) {
				idValue = attValue;
			}
		}

		final IXmlDataStoreIdentifiable result = (IXmlDataStoreIdentifiable) Class.forName(className).newInstance();
		result.setDataStoreId(idValue);
		return result;
	}
}
