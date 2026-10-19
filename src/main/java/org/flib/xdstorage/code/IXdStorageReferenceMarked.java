package org.flib.xdstorage.code;

/**
 * R-53: маркер парсерных стабов инфраструктурных объектов СУБД (узлы B+Дерева).
 *
 * {@link org.flib.xdstorage.serialization.yaml.XdStorageYamlBlockObjectsReader#readReference}
 * материализует ссылку как {@code newInstance()} с заполненным только идентификатором.
 * Для доменных сущностей стабы опознаются через {@link IXdStorageSimpleWrapper}, но
 * инфраструктурные классы обертками не являются — без этого маркера
 * {@code XdStorageObjectUtils.isReference} возвращал false для парсерного стаба,
 * R-38 догрузка не срабатывала, и после переоткрытия базы все спуски по B+Дереву
 * шли по пустому узлу (P0: PERFORMANCE-RECOMMENDATIONS.md §6).
 *
 * Контракт: реальный (материализованный/созданный в памяти) инстанс возвращает false,
 * парсерный стаб — true. Флаг персистится вместе с объектом: на диске у живой ноды
 * он восстановится как false, у стаба (newInstance до парсинга полей) — true по умолчанию.
 */
public interface IXdStorageReferenceMarked {

    boolean isReference();
}
