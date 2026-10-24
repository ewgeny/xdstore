package io.github.xdstorage.observing;

import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM-global registry of id-observable wrappers, keyed by domain class: hands out one
 * stable observable wrapper per (class, object) pair and clears the mapping through a
 * self-registered observer as soon as the wrapper's id is set (deferred-id
 * notification), letting the GC reclaim the entry.
 *
 * <p>The registry is a static {@code ConcurrentHashMap} of per-class segments, each a
 * synchronized {@code WeakHashMap} keyed weakly by the wrapped object — completed
 * transactions are reclaimed by the GC without explicit eviction; concurrent access is
 * safe through the concurrent map's per-segment atomicity. Engine-internal — not part
 * of the public API.</p>
 */
public class XdStorageObserverService {

    /** Registry-only holder: all functionality is static. */
    public XdStorageObserverService() {
    }

    // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ ЯДРА (Ликвидация скрытой утечки памяти Heap):
    // Переводим внутренние сегменты кэша на Collections.synchronizedMap(new WeakHashMap<>()).
    // Использование слабых ссылок (Weak References) гарантирует, что как только транзакция пользователя
    // завершится, сборщик мусора (GC) мгновенно и бесследно вычистит объекты из памяти СУБД,
    // полностью исключая OutOfMemory и race conditions на фазе параллельного вызова .remove()!
    private static final Map<Class<?>, Map<Object, IXdStorageIdObservableWrapper>> wrappers = new ConcurrentHashMap<>();

    /**
     * Returns the observable wrapper registered for the given object, creating and
     * caching it on first request (a repeated call for the same object yields the same
     * wrapper while the entry lives). The freshly created wrapper gets a one-shot
     * observer that removes the registry entry the first time its id is set.
     *
     * @param object the object to expose as an id-observable wrapper; must not be {@code null}
     * @return the cached or newly generated observable wrapper (the underlying wrapping
     *         helper may yield {@code null} when wrapper generation fails)
     */
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
