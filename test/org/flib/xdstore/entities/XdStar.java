package org.flib.xdstore.entities;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdStar implements IXmlDataStoreIdentifiable {

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
		XdStar copy = new XdStar();
		copy.setDataStoreId(id);
		return copy;
	}
}
