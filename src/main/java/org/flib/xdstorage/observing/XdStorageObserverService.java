package org.flib.xdstorage.observing;

import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageObserverService {

    private static Map<Class<?>, Map<Object, IXdStorageIdObservableWrapper>> wrappers = new ConcurrentHashMap<>();

    public static IXdStorageIdObservableWrapper getObservableWrapper(final Object object) {
        final Class<?> cl = object.getClass();

        Map<Object, IXdStorageIdObservableWrapper> clWrappers = wrappers.get(cl);
        if(clWrappers == null) {
            wrappers.putIfAbsent(cl, new ConcurrentHashMap<>());
            clWrappers = wrappers.get(cl);
        } else {
            final IXdStorageIdObservableWrapper wrapper = clWrappers.get(object);
            if(wrapper != null) {
                return wrapper;
            }
        }

        IXdStorageIdObservableWrapper wrapper = XdStorageObjectUtils.wrapAsObservableObject(object);
        clWrappers.putIfAbsent(object, wrapper);
        wrapper = clWrappers.get(object);

        final Map<Object, IXdStorageIdObservableWrapper> finalClWrappers = clWrappers;
        wrapper.addObserver(new XdStorageAbstractIdObserver() {
            @Override
            public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                finalClWrappers.remove(object);
            }
        });

        return wrapper;
    }

}
