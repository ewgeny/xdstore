package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageLoadByGetMethod;
import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.Objects;

/**
 * Fixture for the wrapper JavaBeans-contract tests: a canonical JavaBean with
 * value equals/hashCode (all fields), an is-getter boolean property, a strong
 * (entity-typed) getter, a lazy load-by-get field and one non-accessor method.
 */
public class ValueEqualsBean {

    @XdStorageObjectId
    private Long id;
    private String name;
    private boolean active;
    private ValueChild child;

    @XdStorageLoadByGetMethod
    private ValueChild lazyChild;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public ValueChild getChild() { return child; }
    public void setChild(ValueChild child) { this.child = child; }
    public ValueChild getLazyChild() { return lazyChild; }
    public void setLazyChild(ValueChild lazyChild) { this.lazyChild = lazyChild; }

    /** Non-accessor public method: delegated by the simple wrapper, closed by unmodifiable/observable ones. */
    public String businessMethod() {
        return "business:" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueEqualsBean)) return false;
        final ValueEqualsBean that = (ValueEqualsBean) o;
        return active == that.active && Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, active);
    }

    @Override
    public String toString() {
        return "ValueEqualsBean{id=" + id + ", name='" + name + "', active=" + active + "}";
    }
}
