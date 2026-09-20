package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

import java.util.Collection;

public interface IXdStorageChildCriterionOperator {

    IXdStorageChildCriterionOperator CONTAINS = new IXdStorageChildCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String childFieldName, final Class<?> cl, final String fieldName, final Object value) {
            boolean result = false;

            final Collection<Object> indexValue = record.getChildFieldValue(childFieldName, cl, fieldName);
            for (final Object objectValue : indexValue) {
                if (objectValue == null || !(objectValue instanceof String)) {
                    result = false;
                } else if (value == null || !(value instanceof String)) {
                    result = false;
                } else {
                    result = ((String) objectValue).equals((String) value);
                }
                if (result) {
                    break;
                }
            }
            return result;
        }
    };

    boolean passed(XdStorageSearchIndexRecord record, final String childFieldName, Class<?> cl, String fieldName, Object value);
}
