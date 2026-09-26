package org.flib.xdstorage.serialization;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Контекст отслеживания жизненного цикла маршаллинга графа объектов (Поинт В).
 * Инкапсулирует Identity Map детектора циклов в рамках одной транзакции.
 */
public final class XdStorageJsonContext {

    private final Map<Object, Boolean> cycleDetector = new IdentityHashMap<>();

    public boolean isVisited(final Object object) {
        return cycleDetector.containsKey(object);
    }

    public void visit(final Object object) {
        cycleDetector.put(object, Boolean.TRUE);
    }

    public void remove(final Object object) {
        cycleDetector.remove(object);
    }
}
