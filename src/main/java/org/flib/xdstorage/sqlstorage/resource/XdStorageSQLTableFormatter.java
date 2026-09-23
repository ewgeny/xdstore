package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageSecurityUtils;

/**
 * Выделенный узел декомпозиции (Поинт В).
 * Отвечает за безопасное форматирование и сборку суффиксов реляционных таблиц СУБД.
 */
public class XdStorageSQLTableFormatter {

    public static String formatObjectTable(final String baseTable) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable);
    }

    public static String formatPrevStateObjectTable(final String baseTable) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_prev_state");
    }

    public static String formatCrossDatasourceFkTable(final String fkTable, final String parentTable, final String childTable) {
        return XdStorageSecurityUtils.sanitizeIdentifier(fkTable + "_" + parentTable + "_" + childTable);
    }

    public static String formatPrevStateCrossDatasourceFkTable(final String fkTable, final String parentTable, final String childTable) {
        return XdStorageSecurityUtils.sanitizeIdentifier(fkTable + "_" + parentTable + "_" + childTable + "_prev_state");
    }

    public static String formatLinksTable(final String baseTable, final String fieldName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + fieldName);
    }

    public static String formatPrevStateLinksTable(final String baseTable, final String fieldName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + fieldName + "_prev_state");
    }

    public static String formatIndexTable(final String baseTable, final String indexName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + indexName + "_idx");
    }

    public static String formatPrevStateIndexTable(final String baseTable, final String indexName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + indexName + "_prev_state_idx");
    }

    public static String formatSearchIndexTable(final String baseTable, final String indexName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + indexName);
    }

    public static String formatPrevStateSearchIndexTable(final String baseTable, final String indexName) {
        return XdStorageSecurityUtils.sanitizeIdentifier(baseTable + "_" + indexName + "_prev_state");
    }
}
