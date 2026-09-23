package org.flib.xdstorage.sqlstorage.sql.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.*;

public class XdStorageSQLBatch extends XdStorageAbstractSQLCommand {

    private static final Logger log = LogManager.getLogger(XdStorageSQLBatch.class);

    private Object resourceId;

    private String query;

    private IXdStorageSQLParametersProvider parametersProvider;

    private IXdStorageSQLTypesHelper helper;

    private IXdStorageSQLIdConsumer idConsumer;

    public XdStorageSQLBatch(final Integer priority, final Object resourceId, final String query,
                             final IXdStorageSQLParametersProvider parametersProvider,
                             final IXdStorageSQLTypesHelper helper) {
        this(priority, resourceId, query, parametersProvider, helper, null);
    }

    public XdStorageSQLBatch(final Integer priority, final Object resourceId, final String query,
                             final IXdStorageSQLParametersProvider parametersProvider,
                             final IXdStorageSQLTypesHelper helper, final IXdStorageSQLIdConsumer idConsumer) {
        super(priority);

        this.resourceId = resourceId;
        this.query = query;
        this.parametersProvider = parametersProvider;
        this.helper = helper;
        this.idConsumer = idConsumer;
    }

    public IXdStorageSQLParametersProvider getParametersProvider() {
        return parametersProvider;
    }

    @Override
    public boolean executeAlter(final IXdStorageSQLConnectionProvider connectionProvider,
                             final IXdStorageTransaction transaction) {
        throw new XdStorageRuntimeException("batch doesn't support alter execution");
    }

    @Override
    public void execute(final IXdStorageSQLConnectionProvider connectionProvider,
                        final IXdStorageTransaction transaction) throws SQLException {

        if (log.isDebugEnabled()) {
            log.debug("BATCH QUERY: " + query);
        }

        Connection connection = connectionProvider.getConnection(resourceId, transaction);

        final PreparedStatement st;
        if (idConsumer != null) {
            st = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        } else {
            st = connection.prepareStatement(query);
        }

        // ИСПРАВЛЕНИЕ ПО ПОИНТУ Б: Защищаем пакетную обработку и дескрипторы сгенерированных ключей ResultSet
        try {
            for (int i = 0; i < parametersProvider.getCountRows(); ++i) {
                for (int j = 0; j < parametersProvider.getCountParameters(i); ++j) {
                    helper.setParameter(st, j + 1, parametersProvider.getParameter(i, j));
                }
                st.addBatch();
            }
            st.executeBatch();

            if (idConsumer != null) {
                // Дополнительно оборачиваем ResultSet в try-with-resources для предотвращения утечек курсоров выборки
                try (ResultSet keys = st.getGeneratedKeys()) {
                    for (int i = 0; i < parametersProvider.getCountRows(); ++i) {
                        keys.next();
                        idConsumer.setId(i, keys);
                    }
                }
            }
        } finally {
            if (st != null) {
                st.close();
            }
        }
    }
}
