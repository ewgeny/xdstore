package org.flib.xdstorage.sqlstorage.sql.processor;

import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.SQLException;
import java.util.PriorityQueue;

public class XdStorageSQLPackage {

    private PriorityQueue<IXdStorageSQLCommand> commands = new PriorityQueue<>();

    public void pushCommand(final IXdStorageSQLCommand batch) {
        commands.add(batch);
    }

    public void execute(final IXdStorageSQLConnectionProvider connectionProvider,
                        final IXdStorageTransaction transaction) throws SQLException {
        while (!commands.isEmpty()) {
            final IXdStorageSQLCommand command = commands.poll();
            command.execute(connectionProvider, transaction);
        }
    }

}
