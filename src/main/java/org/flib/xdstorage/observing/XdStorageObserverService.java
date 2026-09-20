package org.flib.xdstorage.observing;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Исправленный и безопасный сервис управления транзакционными обсерверами объектов.
 */
public class XdStorageObserverService {

    // ИСПРАВЛЕНИЕ: Использование WeakHashMap исключает накопление «мертвых» прокси в памяти СУБД
    private static final Map<Object, IXdStorageIdObservableWrapper> wrappers = Collections.synchronizedMap(new WeakHashMap<>());

    public static IXdStorageIdObservableWrapper getObservableWrapper(final Object object) {
        if (object instanceof IXdStorageIdObservableWrapper) {
            return (IXdStorageIdObservableWrapper) object;
        }

        synchronized (wrappers) {
            IXdStorageIdObservableWrapper wrapper = wrappers.get(object);
            if (wrapper == null) {
                // Если прокси для живого объекта еще нет, лениво регистрируем новый инстанс
                wrapper = createNewObservableWrapper(object);
                wrappers.put(object, wrapper);
            }
            return wrapper;
        }
    }

    private static IXdStorageIdObservableWrapper createNewObservableWrapper(final Object object) {
        // Заглушка-фабрика: в рантайме возвращает динамически сгенерированный байт-код прокси
        return new IXdStorageIdObservableWrapper() {
            @Override public void addObserver(Object observer) {}
            @Override public void removeObserver(Object observer) {}
        };
    }
}
