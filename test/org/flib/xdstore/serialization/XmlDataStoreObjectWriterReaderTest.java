package org.flib.xdstore.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.serialization.TestEntity.TestEntityEnum;

public class XmlDataStoreObjectWriterReaderTest {

	public static void main(final String[] args) {
		TestEntity object = new TestEntity();
		object.setCharValue(' ');

		// writeReadTestEntity(object);
		TestEntity object2 = new TestEntity();
		object2.setId("object2");
		object2.setCharValue(' ');

		object.setId("object");
		object.setBooleanValue(true);
		object.setCharValue(' ');
		object.setIntValue(20);
		object.setEnumValue(TestEntityEnum.value0);

		String[][] stringArray = new String[3][];
		stringArray[0] = new String[] {};
		stringArray[1] = new String[] { "string1", "strin2" };
		object.setStringArray(stringArray);

		int[][] intArray = new int[3][];
		intArray[1] = new int[] { 1, 2, 3 };
		intArray[2] = new int[] {};
		object.setIntArray(intArray);

		Object[][] objectArray = new Object[3][];
		objectArray[0] = new String[] { "String" };
		objectArray[1] = new Object[] { null, new String("string"), new Integer(100) };
		objectArray[2] = new Object[] { object2 };
		object.setObjectArray(objectArray);

		Collection<Object> collection = new ArrayList<Object>();
		collection.add(new String("object 1"));
		collection.add(new Integer(2));
		collection.add(object2);
		// collection.add(new Object());
		object.setCollection(collection);

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("key1", new String("value1"));
		map.put("key2", new Integer(2));
		map.put("key3", object2);
		object.setMap(map);
		writeReadTestEntity(object);
	}

	private static void writeReadTestEntity(TestEntity object) {
		XmlDataStoreDefaultObjectsWriter writer = new XmlDataStoreDefaultObjectsWriter();
		try {
			writer.writeObject(object, new ConsoleWriter(), 0);
		} catch (IOException e) {
			e.printStackTrace();
		}

		ByteArrayOutputStream baos;
		Writer byteArrayWriter = new OutputStreamWriter(baos = new ByteArrayOutputStream());
		try {
			writer.writeObject(object, byteArrayWriter, 0);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				byteArrayWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		XmlDataStoreDefaultObjectsReader reader = new XmlDataStoreDefaultObjectsReader();
		Reader byteArrayReader = new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()));
		try {
			Map<String, IXmlDataStoreIdentifiable> readedObject = reader.read(byteArrayReader);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				byteArrayReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
