package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public interface IXdStorageCriterionOperator {

    IXdStorageCriterionOperator IS_NULL = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return record.getFieldValue(fieldName) == null;
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue == null;
        }
    };

    IXdStorageCriterionOperator IS_NOT_NULL = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return record.getFieldValue(fieldName) != null;
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue != null;
        }
    };

    IXdStorageCriterionOperator EQUALS = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            boolean result;

            if (objectValue == null) {
                if (criterionValue == null) {
                    result = true;
                } else {
                    result = false;
                }
            } else {
                result = objectValue.equals(criterionValue);
            }

            return result;
        }
    };

    IXdStorageCriterionOperator NOT_EQUALS = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            boolean result;

            if (objectValue == null) {
                if (criterionValue == null) {
                    result = false;
                } else {
                    result = true;
                }
            } else {
                result = !objectValue.equals(criterionValue);
            }

            return result;
        }
    };

    IXdStorageCriterionOperator CONTAINS = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            boolean result;

            if (objectValue == null || !(objectValue instanceof String)) {
                result = false;
            } else if (criterionValue == null || !(criterionValue instanceof String)) {
                result = false;
            } else {
                result = ((String) objectValue).contains((String) criterionValue);
            }

            return result;
        }
    };

    boolean passed(XdStorageSearchIndexRecord record, String fieldName, Object value);

    boolean passed(Object objectValue, Object criterionValue);
}
