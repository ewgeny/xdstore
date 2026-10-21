package io.github.xdstorage;

public interface IXdStoragePredicate<T> {

    boolean passed(T object);

}
