package org.flib.xdstorage;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Архитектурные политики физического хранения доменных графов объектов СУБД.
 */
public enum XdStoragePolicy {

    /**
     * Хранимый объект записывается непосредственно внутрь файла родительского объекта (Embedded-тип).
     */
    StoreWithParentObject,

    /**
     * Хранимый объект записывается в индивидуальный изолированный файл на диске (один объект — один файл).
     */
    StoreAsSingleObject,

    /**
     * Все хранимые объекты данного класса записываются пакетно в один общий файл класса.
     */
    StoreAsClassObjects;

    // Пуленепробиваемый Immutable-набор политик для быстрого Lock-Free доступа рантайма СУБД
    private static final Set<XdStoragePolicy> INDEPENDENT_POLICIES =
            Collections.unmodifiableSet(EnumSet.of(StoreAsSingleObject, StoreAsClassObjects));

    /**
     * Проверяет, является ли политика автономной (требует ли объект выделенного дискового ресурса DAO).
     */
    public boolean isIndependentResource() {
        return INDEPENDENT_POLICIES.contains(this);
    }

    /**
     * Проверяет, является ли объект встроенным (Embedded встроенный тип).
     */
    public boolean isEmbedded() {
        return this == StoreWithParentObject;
    }
}
