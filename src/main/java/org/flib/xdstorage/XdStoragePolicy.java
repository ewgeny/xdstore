package org.flib.xdstorage;

public enum XdStoragePolicy {

    /**
     * Storaged object will be saved into file of parent object
     */
    StoreWithParentObject,

    /**
     * Storaged object will be saved into single file (one object - one file)
     */
    StoreAsSingleObject,

    /**
     * Storaged object will be saved into file of this objects (all objects in one
     * file)
     */
    StoreAsClassObjects;

}
