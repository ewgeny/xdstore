package org.flib.xdstore.transaction;

import org.flib.xdstore.AbstractXmlDataStoreIdentifiable;

public class XmlDataStoreIndexRecord extends AbstractXmlDataStoreIdentifiable {

	private String resourceId;

	public XmlDataStoreIndexRecord() {
		// do nothing
	}

	public XmlDataStoreIndexRecord(final String objectId, final String resourceId) {
		super.setDataStoreId(objectId);
		this.resourceId = resourceId;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(final String resourceId) {
		this.resourceId = resourceId;
	}
}
