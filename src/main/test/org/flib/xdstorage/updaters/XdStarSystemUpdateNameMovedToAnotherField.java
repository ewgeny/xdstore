package org.flib.xdstorage.updaters;

import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.entities.XdStar;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.structure.update.XdStorageAbstractStructureUpdater;

import java.util.Collection;
import java.util.Date;

public class XdStarSystemUpdateNameMovedToAnotherField extends XdStorageAbstractStructureUpdater<XdStarSystem> {

    public XdStarSystemUpdateNameMovedToAnotherField() {
        super("XdStarSystemUpdateNameMovedToAnotherField");
    }

    @Override
    public Class<?> getDataClass() {
        return XdStarSystem.class;
    }

    @Override
    protected XdStarSystem transform(final XdStorageIdentifiableObject oldStateOfObject) {
        final XdStarSystem result = new XdStarSystem();

        result.setId((String) oldStateOfObject.getId());
        result.setNewName((String) oldStateOfObject.getProperty("name"));

        final Collection<XdStorageIdentifiableObject> stars = (Collection<XdStorageIdentifiableObject>) oldStateOfObject.getProperty("stars");
        for (final XdStorageIdentifiableObject star : stars) {
            final XdStar tmp = new XdStar();
            tmp.setId((String) star.getId());
            tmp.setName((String) star.getProperty("name"));
            tmp.setParent(result);
            result.addStar(tmp);
        }

        final Collection<XdStorageIdentifiableObject> planets = (Collection<XdStorageIdentifiableObject>) oldStateOfObject.getProperty("planets");
        for (final XdStorageIdentifiableObject planet : planets) {
            final XdPlanet tmp = new XdPlanet();
            tmp.setId((Long) planet.getId());
            if (planet.getProperties() != null) {
                tmp.setName((String) planet.getProperty("name"));
                tmp.setWaterPercent((Integer) planet.getProperty("waterPercent"));
                tmp.setDate((Date) planet.getProperty("date"));
            }
            result.addPlanet(tmp);
        }

        return result;
    }
}
