package org.flib.xdstore;

import java.lang.reflect.Field;

public class XmlDataStoreObjectIdField {

	private Field field;

	public XmlDataStoreObjectIdField(final Field field) {
		this.field = field;
	}

	public Object getObjectId(final Object object) {
		field.setAccessible(true);
		try {
			return field.get(object);
		} catch (final IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void setObjectId(final Object object, final Object objectId) {
		field.setAccessible(true);
		try {
			field.set(object, objectId);
		} catch (final IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
}
