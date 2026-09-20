package org.flib.xdstorage.sqlstorage.sql.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class XdStorageSQLCommand extends XdStorageAbstractSQLCommand {

    private static final Logger log = LogManager.getLogger(XdStorageSQLCommand.class);

    private Object resourceId;

    private String query;

    private List<Object> parameters;

    private IXdStorageSQLTypesHelper helper;

    private boolean rollbackTransactionOnFail;

    public XdStorageSQLCommand(final Integer priority, final Object resourceId, final String query,
                               final IXdStorageSQLTypesHelper helper) {
        this (priority, resourceId, query, helper, true);
    }

    public XdStorageSQLCommand(final Integer priority, final Object resourceId, final String query,
                               final IXdStorageSQLTypesHelper helper, final boolean rollbackTransactionOnFail) {
        super(priority);

        this.resourceId = resourceId;
        this.query = query;
        this.helper = helper;
        this.parameters = new ArrayList<>();
        this.rollbackTransactionOnFail = rollbackTransactionOnFail;
    }

    public void addParameter(final Object parameter) {
        parameters.add(parameter);
    }

    @Override
    public boolean executeAlter(final IXdStorageSQLConnectionProvider connectionProvider,
                             final IXdStorageTransaction transaction) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("ALTER QUERY: " + query);
        }

        final Connection connection = connectionProvider.getNewConnection(resourceId);

        try {
            final PreparedStatement st = connection.prepareStatement(query);
            st.execute();

            connection.commit();
        } catch (final SQLException e) {
            connectionProvider.safeRollbackConnection(connection);

            if (!rollbackTransactionOnFail) {
                log.warn("alter command is failed, but marked as no rollback for transaction", e);
                return false;
            } else {
                throw e;
            }
        } finally {
            connectionProvider.safeCloseConnection(connection);
        }
        return true;
    }

    @Override
    public void execute(final IXdStorageSQLConnectionProvider connectionProvider,
                        final IXdStorageTransaction transaction) throws SQLException {

        if (log.isDebugEnabled()) {
            log.debug("SIMPLE QUERY: " + query);
        }

        final Connection connection = connectionProvider.getConnection(resourceId, transaction);

        final PreparedStatement st = connection.prepareStatement(query);
        if (parameters != null && parameters.size() > 0) {
            for (int j = 0; j < parameters.size(); ++j) {
                helper.setParameter(st, j + 1, parameters.get(j));
            }
        }
        st.execute();
    }
}
