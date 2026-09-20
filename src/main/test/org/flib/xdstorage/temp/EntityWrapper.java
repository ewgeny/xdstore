package org.flib.xdstorage.temp;

public class EntityWrapper extends Entity {

    private Entity object;

    public EntityWrapper(final Entity object) {
        this.object = object;

        setName(object.getName());
    }

    @Override
    public void setId(final String id) {
        object.setId(id);
    }
}
