package org.flib.xdstore.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdUniverse implements IXmlDataStoreIdentifiable {

	private String               id;

	private Collection<XdGalaxy> galaxies;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(final String id) {
		this.id = id;
	}

	public Collection<XdGalaxy> getGalaxies() {
		return galaxies;
	}

	public void setGalaxies(Collection<XdGalaxy> galaxies) {
		this.galaxies = galaxies;
	}

	public void addGalaxy(XdGalaxy galaxy) {
		galaxies.add(galaxy);
	}

	public XdGalaxy getGalaxy(final int index) {
		final Iterator<XdGalaxy> it = galaxies.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			final XdGalaxy gal = it.next();
			if (i == index)
				return gal;
		}
		return null;
	}

	public XdGalaxy removeGalaxy(int index) {
		final Iterator<XdGalaxy> it = galaxies.iterator();
		for (int i = 0; it.hasNext(); ++i) {
			final XdGalaxy gal = it.next();
			if (i == index) {
				it.remove();
				return gal;
			}
		}
		return null;
	}

	@Override
	public IXmlDataStoreIdentifiable clone() {
		XdUniverse copy = new XdUniverse();
		copy.setId(id);
		if (galaxies != null) {
			copy.galaxies = new ArrayList<XdGalaxy>(galaxies.size());
			for (final XdGalaxy star : galaxies) {
				copy.galaxies.add((XdGalaxy) star.clone());
			}
		}
		return copy;
	}

}
