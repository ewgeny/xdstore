package org.flib.xdstorage.object;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class XdStorageObject {

    private Class<?> type;

    private Map<String, Object> properties;

    public void setType(Class<?> type) {
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object getProperty(final String name) {
        return properties.get(name);
    }

    public void setProperty(final String name, final Object object) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, object);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XdStorageObject that = (XdStorageObject) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(properties, that.properties);
    }
}
