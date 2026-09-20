package org.flib.xdstorage.sqlstorage.sql.processor;

public interface IXdStorageSQLParametersProvider {

    int getCountRows();

    int getCountParameters(int rowIndex);

    /**
     * @param rowIndex starts with 0
     * @param parameterIndex starts with 0
     * @return
     */
    Object getParameter(int rowIndex, int parameterIndex);
}
