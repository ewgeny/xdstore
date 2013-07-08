package org.flib.xdstore.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.serialization.XmlDataStoreClassProperty;

public final class ObjectUtils {
	
	private ObjectUtils() {
		// do nothing
	}

	private static final Map<Class<?>, Set<XmlDataStoreClassProperty>> classesProperties = new ConcurrentHashMap<Class<?>, Set<XmlDataStoreClassProperty>>();

	public static IXmlDataStoreIdentifiable clone(final IXmlDataStoreIdentifiable object) {
		try {
			IXmlDataStoreIdentifiable result;
			fillObject(result = object.getClass().newInstance(), object);
			return result;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void fillObject(final IXmlDataStoreIdentifiable reference, final IXmlDataStoreIdentifiable object) {
		final Set<XmlDataStoreClassProperty> properties = getProperties(object.getClass());
		for (final XmlDataStoreClassProperty property : properties) {
			final Object tmp = property.get(object);
			final Class<?> cl = tmp != null ? tmp.getClass() : null;
			if (tmp == null || cl.isPrimitive() || cl.isEnum() || isSimpleType(cl, tmp)) {
				property.set(reference, tmp);
			} else if (cl.isArray()) {
				property.set(reference, cloneArray(tmp));
			} else if (tmp instanceof Collection<?>) {
				property.set(reference, cloneCollection(tmp));
			} else if (tmp instanceof Map<?, ?>) {
				property.set(reference, cloneMap(tmp));
			} else {
				property.set(reference, cloneObject(tmp));
			}
		}
	}

	private static Object cloneArray(final Object array) {
		final int length = Array.getLength(array);
		Object clone = Array.newInstance(array.getClass().getComponentType(), length);
		for (int i = 0; i < length; ++i) {
			final Object tmp = Array.get(array, i), value;
			final Class<?> cl = tmp != null ? tmp.getClass() : null;
			if (tmp == null || cl.isEnum() || isSimpleType(cl, tmp)) {
				value = tmp;
			} else if (cl.isArray()) {
				value = cloneArray(tmp);
			} else if (tmp instanceof Collection<?>) {
				value = cloneCollection(tmp);
			} else if (tmp instanceof Map<?, ?>) {
				value = cloneMap(tmp);
			} else {
				value = cloneObject(tmp);
			}
			Array.set(clone, i, value);
		}
		return clone;
	}

	@SuppressWarnings("unchecked")
	private static Object cloneCollection(final Object collection) {
		try {
			final Collection<Object> clone = (Collection<Object>) collection.getClass().newInstance();
			for (final Object object : (Collection<?>) collection) {
				final Class<?> cl = object != null ? object.getClass() : null;
				if (object == null || cl.isEnum() || isSimpleType(cl, object)) {
					clone.add(object);
				} else if (cl.isArray()) {
					clone.add(cloneArray(object));
				} else if (object instanceof Collection<?>) {
					clone.add(cloneCollection(object));
				} else if (object instanceof Map<?, ?>) {
					clone.add(cloneMap(object));
				} else {
					clone.add(cloneObject(object));
				}
			}
			return clone;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Object cloneMap(final Object map) {
		try {
			final Map<Object, Object> clone = (Map<Object, Object>) map.getClass().newInstance();
			for (final Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {

				final Object keytmp = entry.getKey(), key;
				Class<?> cl = keytmp != null ? keytmp.getClass() : null;
				if (keytmp == null || cl.isEnum() || isSimpleType(cl, keytmp)) {
					key = keytmp;
				} else if (cl.isArray()) {
					key = cloneArray(keytmp);
				} else if (keytmp instanceof Collection<?>) {
					key = cloneCollection(keytmp);
				} else if (keytmp instanceof Map<?, ?>) {
					key = cloneMap(keytmp);
				} else {
					key = cloneObject(keytmp);
				}

				final Object valuetmp = entry.getValue(), value;
				cl = valuetmp != null ? valuetmp.getClass() : null;
				if (valuetmp == null || cl.isEnum() || isSimpleType(cl, valuetmp)) {
					value = valuetmp;
				} else if (cl.isArray()) {
					value = cloneArray(valuetmp);
				} else if (valuetmp instanceof Collection<?>) {
					value = cloneCollection(valuetmp);
				} else if (valuetmp instanceof Map<?, ?>) {
					value = cloneMap(valuetmp);
				} else {
					value = cloneObject(valuetmp);
				}

				clone.put(key, value);
			}
			return clone;
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Object cloneObject(final Object object) {
		try {
			final Object clone = object.getClass().newInstance();
			for (Class<?> clazz = object.getClass(); !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
				Field[] fields = clazz.getDeclaredFields();
				for (int i = 0; i < fields.length; i++) {
					fields[i].setAccessible(true);
					// for each class/suerclass, copy all fields
					// from this object to the clone
					final Object tmp = fields[i].get(object), value;
					final Class<?> cl = tmp != null ? tmp.getClass() : null;
					if (tmp == null || cl.isEnum() || isSimpleType(cl, tmp)) {
						value = tmp;
					} else if (cl.isArray()) {
						value = cloneArray(tmp);
					} else if (tmp instanceof Collection<?>) {
						value = cloneCollection(tmp);
					} else if (tmp instanceof Map<?, ?>) {
						value = cloneMap(tmp);
					} else {
						value = cloneObject(tmp);
					}
					fields[i].set(clone, value);
				}
			}
			return clone;
		} catch (IllegalArgumentException | InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static boolean isSimpleType(final Class<?> cl, final Object object) {
		return object instanceof Number || cl == Boolean.class || cl == Character.class || cl == String.class;
	}

	public static Set<XmlDataStoreClassProperty> getProperties(final Class<?> cl) {
		if (classesProperties.containsKey(cl))
			return classesProperties.get(cl);
		final Set<XmlDataStoreClassProperty> properties = new HashSet<XmlDataStoreClassProperty>();
		final Method[] methods = getPublicMethods(cl);
		for (final Method getter : methods) {
			final String name = getter.getName();
			if (getter.getParameterTypes().length == 0 && (name.startsWith("get") || name.startsWith("is"))) {
				final Class<?> tmp = getter.getReturnType();
				if (tmp == void.class || tmp == Void.class)
					continue;
				Method setter;
				try {
					if (name.startsWith("get")) {
						setter = cl.getMethod("set" + name.substring(3), tmp);
						final String fieldName = name.substring(3);
						properties.add(new XmlDataStoreClassProperty(fieldName, setter, getter));
					} else {
						setter = cl.getMethod("set" + name.substring(2), tmp);
						final String fieldName = name.substring(2);
						properties.add(new XmlDataStoreClassProperty(fieldName, setter, getter));
					}
				} catch (NoSuchMethodException | SecurityException e) {
					// e.printStackTrace();
				}
			}
		}
		classesProperties.put(cl, properties);
		return properties;
	}

	public static Map<String, XmlDataStoreClassProperty> getPropertiesMap(final Class<?> cl) {
		final Map<String, XmlDataStoreClassProperty> properties = new HashMap<String, XmlDataStoreClassProperty>();
		final Method[] methods = getPublicMethods(cl);
		for (final Method getter : methods) {
			final String name = getter.getName();
			if (getter.getParameterTypes().length == 0 && (name.startsWith("get") || name.startsWith("is"))) {
				final Class<?> tmp = getter.getReturnType();
				if (tmp == void.class || tmp == Void.class)
					continue;
				Method setter;
				try {
					if (name.startsWith("get")) {
						setter = cl.getMethod("set" + name.substring(3), tmp);
						final String fieldName = name.substring(3);
						properties.put(fieldName, new XmlDataStoreClassProperty(fieldName, setter, getter));
					} else {
						setter = cl.getMethod("set" + name.substring(2), tmp);
						final String fieldName = name.substring(2);
						properties.put(fieldName, new XmlDataStoreClassProperty(fieldName, setter, getter));
					}
				} catch (NoSuchMethodException | SecurityException e) {
					// e.printStackTrace();
				}
			}
		}
		return properties;
	}
	
	private static Method[] getPublicMethods(final Class<?> cl) {
		final List<Method> result = new ArrayList<Method>();
		final Method[] tmp = cl.getDeclaredMethods();
		for(final Method method : tmp) {
			if( (method.getModifiers() & Modifier.PUBLIC) > 0) {
				result.add(method);
			}
		}
		return result.toArray(new Method[result.size()]);
	}
}
