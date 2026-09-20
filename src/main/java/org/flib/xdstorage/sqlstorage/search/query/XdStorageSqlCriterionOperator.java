package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;

import java.util.Iterator;

public enum XdStorageSqlCriterionOperator {

    EQUALS {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            builder.append(field).append(" = '")
                    .append(XdStorageSqlValueFormatter.formatStringValue(helper.simpleTypeValueToString(values[0])))
                    .append('\'');
        }
    },
    NOT_EQUALS {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            builder.append(field).append(" <> '")
                    .append(XdStorageSqlValueFormatter.formatStringValue(helper.simpleTypeValueToString(values[0])))
                    .append('\'');
        }
    },

    GREATER {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final Class<?> cl = values[0].getClass();

            builder.append(helper.buildSqlCastFunction(field, cl)).append(" > ").append(helper.simpleTypeValueToString(values[0]));
        }
    },
    EQUALS_OR_GREATER {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final Class<?> cl = values[0].getClass();

            builder.append(helper.buildSqlCastFunction(field, cl)).append(" >= ").append(helper.simpleTypeValueToString(values[0]));
        }
    },

    LESS {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final Class<?> cl = values[0].getClass();

            builder.append(helper.buildSqlCastFunction(field, cl)).append(" < ").append(helper.simpleTypeValueToString(values[0]));
        }
    },
    EQUALS_OR_LESS {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final Class<?> cl = values[0].getClass();

            builder.append(helper.buildSqlCastFunction(field, cl)).append(" <= ").append(helper.simpleTypeValueToString(values[0]));
        }
    },

    LIKE {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            builder.append(field).append(" LIKE '")
                    .append(XdStorageSqlValueFormatter.formatStringValue(helper.simpleTypeValueToString(values[0])))
                    .append('\'');
        }
    },
    IN {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final StringBuilder inValues = new StringBuilder();
            
            if (values[0] instanceof Iterable) {
                final Iterator<Object> it = ((Iterable<Object>) values[0]).iterator();
                while (it.hasNext()) {
                    final Object object = it.next();
                    inValues.append('\'').append(XdStorageSqlValueFormatter.formatStringValue(helper.simpleTypeValueToString(object))).append('\'');
                    if (it.hasNext()) {
                        inValues.append(',');
                    }
                }
            } else {
                for (int i = 0; i < values.length; ++i) {
                    inValues.append('\'').append(XdStorageSqlValueFormatter.formatStringValue(helper.simpleTypeValueToString(values[i]))).append('\'');
                    if (i < values.length - 1) {
                        inValues.append(',');
                    }
                }
            }

            builder.append(field).append(" IN (").append(inValues.toString()).append(')');
        }
    },
    BETWEEN {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            final Class<?> cl = values[0].getClass();
            
            builder.append(helper.buildSqlCastFunction(field, cl)).append(" BETWEEN ")
                    .append(helper.simpleTypeValueToString(values[0])).append(' ').append(helper.simpleTypeValueToString(values[1]));
        }
    },

    IS_NULL {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            builder.append(field).append(" IS NULL");
        }
    },
    IS_NOT_NULL {
        @Override
        public void place(final StringBuilder builder, final String field, final IXdStorageSQLTypesHelper helper, final Object[] values) {
            builder.append(field).append(" IS NOT NULL");
        }
    };

    public abstract void place(StringBuilder builder, String field, IXdStorageSQLTypesHelper helper, Object[] values);
}
