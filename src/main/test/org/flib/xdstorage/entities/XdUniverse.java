package org.flib.xdstorage.entities;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class XdUniverse {

    @XdStorageObjectId
    private String id;

    private Collection<XdGalaxy> galaxies;

    public String getId() {
        return id;
    }

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
        if (galaxies == null) {
            galaxies = new ArrayList<>();
        }
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
    public String toString() {
        final StringBuilder sb = new StringBuilder("XdUniverse { \r\n");
        sb.append(" idgeneration: ").append(id).append("\r\n");

        if(galaxies != null)
            for (final XdGalaxy galaxy : galaxies) {
                sb.append(", ").append(galaxy.toString()).append("\r\n");
            }
        sb.append(" }");
        return sb.toString();
    }
}
