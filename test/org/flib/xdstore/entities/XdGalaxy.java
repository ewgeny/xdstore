package org.flib.xdstore.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdGalaxy implements IXmlDataStoreIdentifiable {

	private String                   id;

	private XdBlackHole              hole;

	private Collection<XdStarSystem> systems;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
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

	@Override
	public IXmlDataStoreIdentifiable clone() {
		XdGalaxy copy = new XdGalaxy();
		copy.setId(id);
		copy.setHole((XdBlackHole) hole.clone());
		if (systems != null) {
			copy.systems = new ArrayList<XdStarSystem>(systems.size());
			for (final XdStarSystem system : systems) {
				copy.systems.add((XdStarSystem) system.clone());
			}
		}
		return copy;
	}

}
