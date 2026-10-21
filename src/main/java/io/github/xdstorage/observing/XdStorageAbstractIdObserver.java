package io.github.xdstorage.observing;

import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;

public abstract class XdStorageAbstractIdObserver {

    public void observeIfPossible(final Object object) {
        if (object == null)
            throw new XdStorageRuntimeException("cannot observe for null object");

        final Class<?> cl = object.getClass();
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)) {
            final IXdStorageIdObservableWrapper wrapper = (IXdStorageIdObservableWrapper) object;
            wrapper.addObserver(this);
        }
    }

    public abstract void onNewIdIsSet(IXdStorageIdObservableWrapper wrapper, Object id);
}