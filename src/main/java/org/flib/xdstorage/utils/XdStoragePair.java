package org.flib.xdstorage.utils;

import java.util.Objects;

public class XdStoragePair<A, B> {

    public final A a;

    public final B b;

    public XdStoragePair(final A a, final B b) {
        this.a = a;
        this.b = b;
    }

    public A getKey() {
        return a;
    }

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
