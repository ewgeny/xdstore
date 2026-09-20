package org.flib.xdstorage.sqlstorage.sql.processor;

import java.util.ArrayList;
import java.util.List;

public class XdStorageDefaultSQLParametersProvider implements IXdStorageSQLParametersProvider {

    private final List<List<Object>> dataRows;

    public XdStorageDefaultSQLParametersProvider() {
        dataRows = new ArrayList<>();
    }

    @Override
    public int getCountRows() {
        return dataRows.size();
    }

    @Override
    public int getCountParameters(final int rowIndex) {
        return dataRows.get(rowIndex).size();
    }

    @Override
    public Object getParameter(final int rowIndex, final int parameterIndex) {
        return dataRows.get(rowIndex).get(parameterIndex);
    }

    public List<Object> addRow() {
        final List<Object> row = new ArrayList<>();
        dataRows.add(row);
        return row;
    }

    /**
     * This method adds parameter to the last added row.
     *
     * @param parameter
     */
    public void addParameter(final Object parameter) {
        dataRows.get(dataRows.size() - 1).add(parameter);
    }
}
