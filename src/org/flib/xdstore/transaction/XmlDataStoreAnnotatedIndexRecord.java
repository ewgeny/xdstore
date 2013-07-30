package org.flib.xdstore.transaction;

import org.flib.xdstore.XmlDataStoreObjectId;

public class XmlDataStoreAnnotatedIndexRecord {

	@XmlDataStoreObjectId
	private Object objectId;

	private String resourceId;

	public XmlDataStoreAnnotatedIndexRecord() {
		// do nothing
	}

	public XmlDataStoreAnnotatedIndexRecord(final Object objectId, final String resourceId) {
		this.objectId = objectId;
		this.resourceId = resourceId;
	}

	public Object getObjectId() {
		return objectId;
	}

	public void setObjectId(final Object objectId) {
		this.objectId = objectId;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(final String resourceId) {
		this.resourceId = resourceId;
	}

}
