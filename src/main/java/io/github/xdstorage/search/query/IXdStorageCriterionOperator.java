package io.github.xdstorage.search.query;

import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

import java.util.Collection;

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

    // =========================================================================
    // FP-1a (docs/tasks/2.1.0/09): range operators. Comparison contract:
    //   - nulls NEVER satisfy a range operator (IS_NULL / IS_NOT_NULL are the null tests);
    //   - comparisons are per-field-type (numbers numerically, strings lexicographically,
    //     Comparable contract otherwise);
    //   - a comparable-type mismatch fails LOUDLY, never silently-empty;
    //   - a null/absent criterion bound fails loudly too — it is a query bug, not a filter.
    // On the ordered search-index tree GT/GTE/LT/LTE/BETWEEN evaluate as a bounded ordered
    // walk (early termination at the upper bound) and IN as one membership-filtered walk;
    // on any non-ordered surface they degrade to the scan with identical result semantics.
    // =========================================================================

    IXdStorageCriterionOperator GT = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue != null && compareForRange(objectValue, criterionValue, "GT") > 0;
        }
    };

    IXdStorageCriterionOperator GTE = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue != null && compareForRange(objectValue, criterionValue, "GTE") >= 0;
        }
    };

    IXdStorageCriterionOperator LT = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue != null && compareForRange(objectValue, criterionValue, "LT") < 0;
        }
    };

    IXdStorageCriterionOperator LTE = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            return objectValue != null && compareForRange(objectValue, criterionValue, "LTE") <= 0;
        }
    };

    IXdStorageCriterionOperator BETWEEN = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            if (!(criterionValue instanceof XdStorageRange)) {
                throw new XdStorageRuntimeException("BETWEEN requires an " + XdStorageRange.class.getSimpleName()
                        + " (from, to) criterion value, got "
                        + (criterionValue == null ? "null" : criterionValue.getClass().getName()));
            }
            if (objectValue == null) {
                return false;
            }
            final XdStorageRange range = (XdStorageRange) criterionValue;
            // both bounds INCLUSIVE (pinned contract, docs/tasks/2.1.0/09)
            return compareForRange(objectValue, range.getFrom(), "BETWEEN lower") >= 0
                    && compareForRange(objectValue, range.getTo(), "BETWEEN upper") <= 0;
        }
    };

    IXdStorageCriterionOperator IN = new IXdStorageCriterionOperator() {
        @Override
        public boolean passed(final XdStorageSearchIndexRecord record, final String fieldName, final Object value) {
            return passed(record.getFieldValue(fieldName), value);
        }

        @Override
        public boolean passed(final Object objectValue, final Object criterionValue) {
            // nulls never satisfy the range-operator family (IN included)
            if (objectValue == null) {
                return false;
            }
            for (final Object candidate : valuesOf(criterionValue)) {
                if (objectValue.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
    };

    /** Comparison core of the range family: the caller guarantees a non-null objectValue
     * (null never satisfies — the operators short-circuit); a null bound or a
     * non-comparable/mismatched type fails loudly. */
    static int compareForRange(final Object objectValue, final Object bound, final String operator) {
        if (bound == null) {
            throw new XdStorageRuntimeException("range operator " + operator
                    + " requires a non-null criterion bound — IS_NULL / IS_NOT_NULL are the null tests");
        }
        if (!(objectValue instanceof Comparable)) {
            throw new XdStorageRuntimeException("range operator " + operator + ": indexed value of type "
                    + objectValue.getClass().getName() + " is not Comparable — mixed-type comparison fails loudly");
        }
        try {
            return ((Comparable) objectValue).compareTo(bound);
        } catch (final ClassCastException e) {
            throw new XdStorageRuntimeException("range operator " + operator + ": indexed value of type "
                    + objectValue.getClass().getName() + " is not comparable with the criterion bound of type "
                    + bound.getClass().getName() + " — mixed-type comparison fails loudly", e);
        }
    }

    /** IN accepts a Collection or an array; anything else fails loudly. */
    static Iterable<?> valuesOf(final Object criterionValue) {
        if (criterionValue instanceof Collection) {
            return (Collection<?>) criterionValue;
        }
        if (criterionValue instanceof Object[]) {
            return java.util.Arrays.asList((Object[]) criterionValue);
        }
        throw new XdStorageRuntimeException("IN requires a Collection or an array criterion value, got "
                + (criterionValue == null ? "null" : criterionValue.getClass().getName()));
    }

    boolean passed(XdStorageSearchIndexRecord record, String fieldName, Object value);

    boolean passed(Object objectValue, Object criterionValue);
}
