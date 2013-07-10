package org.flib.xdstore.trigger;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public interface IXmlDataStoreTrigger<T extends IXmlDataStoreIdentifiable> {

	XmlDataStoreTriggerType getType();

	Class<T> getClazz();

	/**
	 * If this method works with store, then this one must start and finish
	 * transaction.
	 * 
	 * @param object
	 *            Object from store.
	 */
	void perform(T object);

}
