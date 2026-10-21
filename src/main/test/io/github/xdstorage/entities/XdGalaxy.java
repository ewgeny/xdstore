package io.github.xdstorage.entities;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectFieldProperties;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectIdIndexType;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.idgeneration.XdStorageIdGeneratorType;
import io.github.xdstorage.idgeneration.MyStringIdGenerator;
import io.github.xdstorage.index.XdStorageIndexType;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 10)
public class XdGalaxy {

    @XdStorageObjectId
    @XdStorageObjectFieldProperties(
            idGeneratorType = XdStorageIdGeneratorType.CUSTOM_GENERATOR,
            idGeneratorClass = MyStringIdGenerator.class)
    private String id;

    private XdBlackHole hole;

    private Collection<XdStarSystem> systems;

    public String getId() {
        return id;
    }

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
        if (systems == null) {
            systems = new LinkedList<>();
        }
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
    public String toString() {
        final StringBuilder sb = new StringBuilder("XdGalaxy { \r\n");
        sb.append(" idgeneration: ").append(id).append("\r\n");

        if(systems != null)
            for (final XdStarSystem system : systems) {
                sb.append(", ").append(system.toString()).append("\r\n");
            }
        sb.append(" }");
        return sb.toString();
    }
}
