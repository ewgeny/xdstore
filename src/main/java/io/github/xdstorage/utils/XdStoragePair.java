package io.github.xdstorage.utils;

import java.util.Objects;

/**
 * A minimal immutable 2-tuple (the {@code a}/{@code b} fields are also exposed through the
 * {@code getKey}/{@code getValue} views named after {@code java.util.Map.Entry}). Value
 * semantics on both components; thread-safe when its components are. Engine-internal — not
 * part of the public API.
 *
 * @param <A> the type of the first component
 * @param <B> the type of the second component
 */
public class XdStoragePair<A, B> {

    /** The first component (the key view). */
    public final A a;

    /** The second component (the value view). */
    public final B b;

    /**
     * Creates the pair.
     *
     * @param a the first component, may be {@code null}
     * @param b the second component, may be {@code null}
     */
    public XdStoragePair(final A a, final B b) {
        this.a = a;
        this.b = b;
    }

    /** The key view: the first component.
     *
     * @return the first component {@link #a}
     */
    public A getKey() {
        return a;
    }

    /** The value view: the second component.
     *
     * @return the second component {@link #b}
     */
    public B getValue() {
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStoragePair<?, ?> that = (XdStoragePair<?, ?>) o;
        return Objects.equals(a, that.a) &&
                Objects.equals(b, that.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
