package io.github.xdstorage.object;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-3a (docs/tasks/2.1.0/06): the object-model value contracts — the base classes the
 * serialization path moves around (XdStorageObject's property bag, XdStorageIdentifiableObject's
 * id + equals/hashCode). The god converter (XdStorageObjectConverter) is out of scope here per
 * the task; these are the public value semantics.
 */
@DisplayName("T-3a: object model value contracts")
public class XdStorageObjectModelContractTest {

    @Test
    @DisplayName("the property bag: lazy init on write, type; reading before the first write NPEs (actual contract)")
    public void propertyBag() {
        final XdStorageObject o = new XdStorageObject();
        assertNull(o.getProperties(), "no bag until the first write");
        // actual contract (pinned, sharp edge): the write path lazily creates the bag,
        // the read path does not null-check it — a pre-write read throws NPE
        assertThrows(NullPointerException.class, () -> o.getProperty("anything"));

        o.setProperty("resourceId", "r-1");
        assertEquals("r-1", o.getProperty("resourceId"));
        assertNotNull(o.getProperties(), "the bag is lazily created on the first write");

        final Map<String, Object> bag = new HashMap<>();
        bag.put("k", "v");
        o.setProperties(bag);
        assertEquals("v", o.getProperty("k"), "an explicitly set bag replaces the lazy one");

        o.setType(String.class);
        assertEquals(String.class, o.getType());
    }

    @Test
    @DisplayName("XdStorageObject equals: same class + same properties")
    public void baseEquals() {
        final XdStorageObject a = new XdStorageObject();
        a.setProperty("x", 1);
        final XdStorageObject b = new XdStorageObject();
        b.setProperty("x", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setProperty("x", 2);
        assertNotEquals(a, b);
        assertNotEquals(a, null);
        assertNotEquals(a, new Object(), "different class → not equal");
    }

    @Test
    @DisplayName("XdStorageIdentifiableObject: id in equals/hashCode, same-class rule")
    public void identifiableEqualsAndId() {
        final XdStorageIdentifiableObject a = new XdStorageIdentifiableObject();
        a.setId("id-1");
        a.setProperty("x", 1);
        final XdStorageIdentifiableObject b = new XdStorageIdentifiableObject();
        b.setId("id-1");
        b.setProperty("x", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setId("id-2");
        assertNotEquals(a, b, "a different id breaks equality even with equal properties");

        b.setId(null);
        assertNotEquals(a, b, "a null id is not equal to a set id");
    }

    @Test
    @DisplayName("subclass instances are never equal to base instances of equal state")
    public void subclassNeverEqualsBase() {
        final XdStorageObject base = new XdStorageObject();
        base.setProperty("x", 1);
        final XdStorageIdentifiableObject sub = new XdStorageIdentifiableObject();
        sub.setProperty("x", 1);
        assertNotEquals(base, sub, "getClass() discipline: base vs subclass differ");
    }
}
