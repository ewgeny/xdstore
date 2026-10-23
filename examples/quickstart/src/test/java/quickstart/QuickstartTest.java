package quickstart;

import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.XdStorageProvider;
import io.github.xdstorage.search.query.IXdStorageCriterionOperator;
import io.github.xdstorage.search.query.XdStoragePrimaryCriterion;
import io.github.xdstorage.search.query.XdStorageRange;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The whole XdStorage lifecycle in one readable flow:
 * open -> transaction -> save -> indexed queries (EQUALS, range, ORDER BY + LIMIT, count)
 * -> commit -> shutdown -> REOPEN from disk -> load -> shutdown.
 *
 * <p>This is the code to copy. It follows docs/EMBEDDING.md's lifecycle contract exactly:
 * a unique storage name and folder per run, explicit transactions with commit/rollback in
 * try/catch, and shutdown before reopening the same folder.
 */
@DisplayName("XdStorage quickstart: the full lifecycle")
public class QuickstartTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("open -> save -> query -> commit -> reopen -> load -> shutdown")
    public void fullLifecycle() throws Exception {
        // --- 1. open ------------------------------------------------------------
        // A storage is one folder. The provider keeps a named registry: unique name +
        // unique folder per run (a repeat call with a known name returns the OPEN instance).
        final String storageName = "quickstart-" + System.nanoTime();
        final String folder = tempDir.resolve("db").toString();
        IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(storageName, folder, 64);

        // --- 2. save inside an explicit transaction ------------------------------
        final IXdStorageTransaction tx = storage.beginTransaction(60_000L);
        try {
            storage.save(Arrays.asList(
                    new Customer("c-1", "Alice", "DE"),
                    new Customer("c-2", "Bruno", "FR"),
                    new Customer("c-3", "Clara", "DE"),
                    new Customer("c-4", "Dana", "US"),
                    new Customer("c-5", "Emil", "DE")), tx);
            for (int i = 1; i <= 10; i++) {
                storage.save(new Account("a-" + i, "owner-" + i, (long) i * 100), tx);
            }
            storage.commitTransaction(tx);
        } catch (final RuntimeException e) {
            storage.rollbackTransaction(tx);
            throw e;
        }

        // --- 3. query: EQUALS on the secondary index -----------------------------
        List<String> germanIds = queryCountry(storage, "DE");
        assertEquals(Arrays.asList("c-1", "c-3", "c-5"), germanIds, "EQUALS hits the by_country index");

        // --- 4. query: a RANGE on the ordered index (inclusive bounds) -----------
        // BETWEEN 300..700 matches a-3..a-7; null balances would never satisfy a range.
        // Contract note: WITHOUT shaping the result set is unordered — apply ORDER BY
        // (step 5) when the walk order matters.
        final XdStorageSearchQuery midBalances = new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.BETWEEN,
                        new XdStorageRange(300L, 700L)), null);
        assertEquals(Arrays.asList("a-3", "a-4", "a-5", "a-6", "a-7"),
                accountIds(storage, midBalances).stream().sorted().collect(Collectors.toList()),
                "BETWEEN is inclusive on both bounds; the result set is exact");

        // --- 5. shape it: ORDER BY DESC + LIMIT (the walk stops at the page end) -
        final XdStorageSearchQuery topPage = new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.GTE, 300L), null)
                .orderByPrimary(true)   // descending key order
                .limit(3);              // pushed down: the walk stops after 3 keys
        assertEquals(Arrays.asList("a-10", "a-9", "a-8"),
                accountIds(storage, topPage), "DESC + LIMIT returns the top page in key order");

        // --- 6. count without materializing the result ---------------------------
        final IXdStorageTransaction countTx = storage.beginTransaction(60_000L);
        try {
            assertEquals(5L, storage.count(Account.class, "by_balance", midBalances, countTx),
                    "count(q) is exactly load(q).size() on the same snapshot");
            storage.commitTransaction(countTx);
        } catch (final RuntimeException e) {
            storage.rollbackTransaction(countTx);
            throw e;
        }

        // --- 7. shutdown, then REOPEN the same folder from disk ------------------
        storage.shutdown();
        storage = XdStorageProvider.newOrGetFileStorage(storageName, folder, 64);

        final IXdStorageTransaction reopenTx = storage.beginTransaction(60_000L);
        try {
            final Customer c3 = storage.load(Customer.class, "c-3", reopenTx);
            assertEquals("Clara", c3.getName(), "the object survived the close/open cycle");

            // EQUALS on the String-keyed index answers from persisted state...
            assertEquals(Arrays.asList("c-1", "c-3", "c-5"),
                    queryCountry(storage, "DE"), "the index answered from persisted state");

            // ...and so do RANGE and COUNT on the numeric index (BUG-E — docs/tasks/2.1.0/18 —
            // used to crash here: the persisted search keys came back as raw Strings; the
            // reopen path restores them to the indexed field's type, one walk per tree).
            assertEquals(Arrays.asList("a-3", "a-4", "a-5", "a-6", "a-7"),
                    accountIds(storage, midBalances).stream().sorted().collect(Collectors.toList()),
                    "BETWEEN after the reopen: keys restored to the indexed field's type");
            assertEquals(5L, storage.count(Account.class, "by_balance", midBalances, reopenTx),
                    "count after the reopen: the same walk, the same answer");
            storage.commitTransaction(reopenTx);
        } catch (final RuntimeException e) {
            storage.rollbackTransaction(reopenTx);
            throw e;
        }

        // --- 8. shutdown ----------------------------------------------------------
        storage.shutdown();
    }

    private List<String> queryCountry(final IXdFileStorage st, final String country) throws Exception {
        final IXdStorageTransaction tx = st.beginTransaction(60_000L);
        try {
            final XdStorageSearchQuery query = new XdStorageSearchQuery(
                    new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, country), null);
            final List<String> ids = st.load(Customer.class, "by_country", query, tx)
                    .stream().map(Customer::getId).sorted().collect(Collectors.toList());
            st.commitTransaction(tx);
            return ids;
        } catch (final RuntimeException e) {
            st.rollbackTransaction(tx);
            throw e;
        }
    }

    private List<String> accountIds(final IXdFileStorage st, final XdStorageSearchQuery query) throws Exception {
        final IXdStorageTransaction tx = st.beginTransaction(60_000L);
        try {
            final List<String> ids = st.load(Account.class, "by_balance", query, tx)
                    .stream().map(Account::getId).collect(Collectors.toList());
            st.commitTransaction(tx);
            return ids;
        } catch (final RuntimeException e) {
            st.rollbackTransaction(tx);
            throw e;
        }
    }
}
