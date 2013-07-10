package org.flib.xdstore.serialization;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class XmlDataStoreClassProperty {

	public final String  name;

	private final Method setter;

	private final Method getter;

	public XmlDataStoreClassProperty(final String name, final Method setter, final Method getter) {
		this.name = name;
		this.setter = setter;
		this.getter = getter;
	}

	public Class<?> getClazz() {
		return getter.getReturnType();
	}

	public <T> void set(final Object obj, final T value) {
		try {
			setter.invoke(obj, value);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T get(final Object obj) {
		try {
			return (T) getter.invoke(obj, new Object[] {});
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		XmlDataStoreClassProperty other = (XmlDataStoreClassProperty) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}