package org.flib.xdstorage.sqlstorage.sql.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageSQLProcessor {

    private static final Logger log = LogManager.getLogger(XdStorageSQLProcessor.class);

    private IXdStorageSQLConnectionProvider connectionProvider;

    private final List<IXdStorageSQLCommand> alterPackages = new LinkedList<>();

    private Map<String, XdStorageSQLPackage> packages = new ConcurrentHashMap<>();

    public XdStorageSQLProcessor(final IXdStorageSQLConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void pushAlterCommand(final IXdStorageTransaction transaction, final IXdStorageSQLCommand command) {
        synchronized (alterPackages) {
            alterPackages.add(command);
        }
    }

    public void pushCommand(final IXdStorageTransaction transaction, final IXdStorageSQLCommand command) {
        final XdStorageSQLPackage tpackage = packages
                .computeIfAbsent(transaction.getTransactionId(), transactionId -> new XdStorageSQLPackage());
        tpackage.pushCommand(command);
    }

    public void executeAlter(final IXdStorageTransaction transaction) throws XdStorageException {
        List<IXdStorageSQLCommand> commands;
        synchronized (alterPackages) {
            commands = new ArrayList<>(alterPackages);
            alterPackages.clear();
        }

        try {
            final Iterator<IXdStorageSQLCommand> it = commands.iterator();
            while (it.hasNext()) {
                final IXdStorageSQLCommand command = it.next();
                if (!command.executeAlter(connectionProvider, transaction)) {
                    pushAlterCommand(transaction, command);
                }
                it.remove();
            }
        } catch (final SQLException e) {
            log.error("cannot execute SQL command", e);
            XdStorageException toThrow = new XdStorageException(e);
            if (e.getNextException() != null) {
                log.error("the next exception", e.getNextException());
                toThrow = new XdStorageException(e.getNextException());
            }

            if (!commands.isEmpty()) {
                synchronized (alterPackages) {
                    alterPackages.addAll(commands);
                }
            }

            throw toThrow;
        }
    }

    public void execute(final IXdStorageTransaction transaction) throws XdStorageException {
        final XdStorageSQLPackage tpackage = packages.remove(transaction.getTransactionId());
        if (tpackage != null) {
            try {
                tpackage.execute(connectionProvider, transaction);

                connectionProvider.commitAll(transaction);
            } catch (final SQLException e) {
                connectionProvider.rollbackAll(transaction);

                log.error("cannot execute SQL command", e);
                XdStorageException toThrow = new XdStorageException(e);
                if (e.getNextException() != null) {
                    log.error("the next exception", e.getNextException());
                    toThrow = new XdStorageException(e.getNextException());
                }

                throw toThrow;
            }
        }
    }
}
