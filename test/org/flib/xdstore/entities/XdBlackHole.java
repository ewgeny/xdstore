package org.flib.xdstore.entities;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdBlackHole implements IXmlDataStoreIdentifiable {

	private String id;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public IXmlDataStoreIdentifiable clone() {
		XdBlackHole copy = new XdBlackHole();
		copy.setId(id);
		return copy;
	}
}
