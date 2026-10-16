package org.flib.xdstorage.resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ФАЗА 3B: реестр канонических инстансов инфраструктуры B+Дерева (деревья/узлы),
 * на один storage (держится в XdStorageServicesLocator, чистится при shutdown).
 *
 * Зачем: ресурсprepare() разбирает файлы с диска в НОВЫЕ инстансы, а писатель мутирует
 * СВОЙ живой граф в памяти — читатель получал parse-копию с заглушками root/firstLeaf
 * вместо канонического графа (двусмысленность инстанций). Реестр делает инстанцию
 * канонической: parse-копия при fillCache УСТУПАЕТ место живому инстансу, если тот
 * зарегистрирован; живой инстанс регистрируется при insert/update (materializeForCache).
 *
 * Удаление записей: только clear() (shutdown ресурса) — удаленные ноды остаются в
 * реестре до конца жизни storage (осознанный компромисс против воскрешения нод при
 * откате удаления и последующем reparse; утечка ограничена числом созданных нод).
 */
public class XdStorageCanonicalInstanceRegistry {

    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    public Object adoptOrRegister(final Class<?> clazz, final Object id, final Object candidate) {
        if (clazz == null || id == null || candidate == null) {
            return candidate;
        }
        return instances.computeIfAbsent(clazz.getName() + "#" + id, k -> candidate);
    }

    public Object get(final Class<?> clazz, final Object id) {
        if (clazz == null || id == null) {
            return null;
        }
        return instances.get(clazz.getName() + "#" + id);
    }

    public void clear() {
        instances.clear();
    }
}
