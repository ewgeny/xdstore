package org.flib.xdstore.serialization;

import java.util.Collection;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class TestEntity implements IXmlDataStoreIdentifiable {

	public static enum TestEntityEnum {
		value0, value1;
	}

	private String              id;

	private int                 intValue;

	private char                charValue;

	private boolean             booleanValue;

	private TestEntityEnum      enumValue;

	private Collection<Object>  collection;

	private Map<String, Object> map;

	private String[][]          stringArray;

	private Object[][]          objectArray;

	private int[][]             intArray;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(final String id) {
		this.id = id;
	}

	public int getIntValue() {
		return intValue;
	}

	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}

	public char getCharValue() {
		return charValue;
	}

	public void setCharValue(char charValue) {
		this.charValue = charValue;
	}

	public boolean isBooleanValue() {
		return booleanValue;
	}

	public void setBooleanValue(boolean booleanValue) {
		this.booleanValue = booleanValue;
	}

	public Collection<Object> getCollection() {
		return collection;
	}

	public void setCollection(Collection<Object> collection) {
		this.collection = collection;
	}

	public Map<String, Object> getMap() {
		return map;
	}

	public void setMap(Map<String, Object> map) {
		this.map = map;
	}

	public String[][] getStringArray() {
		return stringArray;
	}

	public void setStringArray(String[][] stringArray) {
		this.stringArray = stringArray;
	}

	public Object[][] getObjectArray() {
		return objectArray;
	}

	public void setObjectArray(Object[][] objectArray) {
		this.objectArray = objectArray;
	}

	public int[][] getIntArray() {
		return intArray;
	}

	public void setIntArray(int[][] intArray) {
		this.intArray = intArray;
	}

	public TestEntityEnum getEnumValue() {
		return enumValue;
	}

	public void setEnumValue(TestEntityEnum enumValue) {
		this.enumValue = enumValue;
	}

}
