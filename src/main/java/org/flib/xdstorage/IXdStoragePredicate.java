package org.flib.xdstorage;

public interface IXdStoragePredicate<T> {

    boolean passed(T object);

}
