package org.flib.xdstorage.observing;

import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageObserverService {

    // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ ЯДРА (Ликвидация скрытой утечки памяти Heap):
    // Переводим внутренние сегменты кэша на Collections.synchronizedMap(new WeakHashMap<>()).
    // Использование слабых ссылок (Weak References) гарантирует, что как только транзакция пользователя
    // завершится, сборщик мусора (GC) мгновенно и бесследно вычистит объекты из памяти СУБД,
    // полностью исключая OutOfMemory и race conditions на фазе параллельного вызова .remove()!
    private static final Map<Class<?>, Map<Object, IXdStorageIdObservableWrapper>> wrappers = new ConcurrentHashMap<>();

    public static IXdStorageIdObservableWrapper getObservableWrapper(final Object object) {
        final Class<?> cl = object.getClass();

        // Атомарно инициализируем потокобезопасный сегмент Weak-ссылок для конкретного доменного класса
        Map<Object, IXdStorageIdObservableWrapper> clWrappers = wrappers.computeIfAbsent(cl,
                k -> Collections.synchronizedMap(new WeakHashMap<>()));

        // Быстрый синхронизированный поиск уже существующего прокси в Weak-сегменте
        IXdStorageIdObservableWrapper wrapper = clWrappers.get(object);
        if (wrapper != null) {
            return wrapper;
        }

        // Если прокси еще нет — генерируем реактивную Observable-обертку
        wrapper = XdStorageObjectUtils.wrapAsObservableObject(object);
        clWrappers.put(object, wrapper);

        final Map<Object, IXdStorageIdObservableWrapper> finalClWrappers = clWrappers;
        wrapper.addObserver(new XdStorageAbstractIdObserver() {
            @Override
            public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                // Fail-safe очистка сегмента: если из-за параллельных гонок потоков
                // объект уже был вытеснен или удален, метод отработает без побочных эффектов
                finalClWrappers.remove(object);
            }
        });

        return wrapper;
    }
}
