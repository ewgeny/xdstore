package org.flib.xdstore.entities;

import java.util.ArrayList;
import java.util.Collection;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XdStarSystem implements IXmlDataStoreIdentifiable {

	private String               id;

	private Collection<XdStar>   stars;

	private Collection<XdPlanet> planets;

	@Override
	public String getDataStoreId() {
		return id;
	}

	@Override
	public void setDataStoreId(String id) {
		this.id = id;
	}

	public Collection<XdStar> getStars() {
		return stars;
	}

	public void setStars(Collection<XdStar> stars) {
		this.stars = stars;
	}

	public void addStar(XdStar star) {
		stars.add(star);
	}

	public Collection<XdPlanet> getPlanets() {
		return planets;
	}

	public void setPlanets(Collection<XdPlanet> planets) {
		this.planets = planets;
	}

	public void addPlanet(XdPlanet planet) {
		planets.add(planet);
	}

	@Override
	public IXmlDataStoreIdentifiable clone() {
		XdStarSystem copy = new XdStarSystem();
		copy.setDataStoreId(id);
		if (stars != null) {
			copy.stars = new ArrayList<XdStar>(stars.size());
			for (final XdStar star : stars) {
				copy.stars.add((XdStar) star.clone());
			}
		}
		if (planets != null) {
			copy.planets = new ArrayList<XdPlanet>(planets.size());
			for (final XdPlanet planet : planets) {
				copy.planets.add((XdPlanet) planet.clone());
			}
		}
		return copy;
	}
}
