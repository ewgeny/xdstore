package org.flib.xdstore.entities;

import java.util.Collection;

import org.flib.xdstore.AbstractXmlDataStoreIdentifiable;

public class XdStarSystem extends AbstractXmlDataStoreIdentifiable {

	private Collection<XdStar>   stars;

	private Collection<XdPlanet> planets;

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

}
