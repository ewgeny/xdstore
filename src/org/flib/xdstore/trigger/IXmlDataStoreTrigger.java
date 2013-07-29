package org.flib.xdstore.trigger;

public interface IXmlDataStoreTrigger<T> {

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
