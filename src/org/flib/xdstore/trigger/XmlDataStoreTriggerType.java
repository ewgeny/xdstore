package org.flib.xdstore.trigger;


public enum XmlDataStoreTriggerType {

	/**
	 * Trigger with this type will performed then object was inserted by commit transaction.
	 */
	Insert,
	
	/**
	 * Trigger with this type will performed then object was updated by commit transaction.
	 */
	Update,
	
	/**
	 * Trigger with this type will performed then object was deleted by commit transaction.
	 */
	Delete;
	
}
