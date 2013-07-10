package org.flib.xdstore;

public interface IXmlDataStorePredicate<T extends IXmlDataStoreIdentifiable> {

	boolean passed(final T object);

}
