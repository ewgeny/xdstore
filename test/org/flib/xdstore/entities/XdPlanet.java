package org.flib.xdstore.entities;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdPlanet implements IXmlDataStoreIdentifiable {

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
		XdPlanet copy = new XdPlanet();
		copy.setId(id);
		return copy;
	}
}
