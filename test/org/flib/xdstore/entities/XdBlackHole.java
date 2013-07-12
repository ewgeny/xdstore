package org.flib.xdstore.entities;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdBlackHole implements IXmlDataStoreIdentifiable {

	private String id;

	@Override
	public String getDataStoreId() {
		return id;
	}

	@Override
	public void setDataStoreId(String id) {
		this.id = id;
	}

	@Override
	public IXmlDataStoreIdentifiable clone() {
		XdBlackHole copy = new XdBlackHole();
		copy.setDataStoreId(id);
		return copy;
	}
}
