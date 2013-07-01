package org.flib.xdstore;

public enum XmlDataStorePolicy {

	/**
	 * Stored object will be saved into file of parent object
	 */
	ParentObjectFile,

	/**
	 * Stored object will be saved into single file (one object - one file)
	 */
	SingleObjectFile,

	/**
	 * Stored object will be saved into file of this objects (all objects in one
	 * file)
	 */
	ClassObjectsFile;

}
