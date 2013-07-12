package org.flib.xdstore;

/**
 * This interface must be implemented all serialize classes.
 * 
 * @author Евгений
 * 
 */
public interface IXmlDataStoreIdentifiable {

	String getDataStoreId();

	void setDataStoreId(String dataStoreId);
}
