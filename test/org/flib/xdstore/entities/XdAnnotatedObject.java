package org.flib.xdstore.entities;

import org.flib.xdstore.XmlDataStoreObjectId;


public class XdAnnotatedObject {

	@XmlDataStoreObjectId
	private Long	objectId;
	
    public Long getObjectId() {
    	return objectId;
    }
	
    public void setObjectId(final Long objectId) {
    	this.objectId = objectId;
    }
	
}
