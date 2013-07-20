package org.flib.xdstore.entities;

import java.util.Collection;
import java.util.Iterator;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdGalaxy implements IXmlDataStoreIdentifiable {

	private String                   id;

	private XdBlackHole              hole;

	private Collection<XdStarSystem> systems;

	@Override
	public String getDataStoreId() {
		return id;
	}

	@Override
	public void setDataStoreId(String id) {
		this.id = id;
	}

	public XdBlackHole getHole() {
		return hole;
	}

	public void setHole(XdBlackHole hole) {
		this.hole = hole;
	}

	public Collection<XdStarSystem> getSystems() {
		return systems;
	}

	public void setSystems(Collection<XdStarSystem> systems) {
		this.systems = systems;
	}

	public void addSystem(final XdStarSystem system) {
		systems.add(system);
	}

	public XdStarSystem getSystem(int index) {
		final Iterator<XdStarSystem> it = systems.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			final XdStarSystem ssys = it.next();
			if (i == index)
				return ssys;
		}
		return null;
	}

	public XdStarSystem removeSystem(int index) {
		final Iterator<XdStarSystem> it = systems.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			final XdStarSystem ssys = it.next();
			if (i == index) {
				it.remove();
				return ssys;
			}
		}
		return null;
	}
}
