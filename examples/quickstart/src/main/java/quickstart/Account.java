package quickstart;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/**
 * A second domain entity demonstrating the range operators and result shaping:
 * the {@code by_balance} search index is ordered, so its primary field ({@code balance},
 * the FIRST indexed field) supports GT/GTE/LT/LTE/BETWEEN/IN seeks, ORDER BY and
 * LIMIT/OFFSET — evaluated as a bounded walk of the index tree, not a scan.
 *
 * <p>{@code balance} is a boxed {@code Long}: nulls never satisfy a range operator
 * (the null test is IS_NULL / IS_NOT_NULL).
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(indexName = "by_balance", indexFieldNames = {"balance"})
public class Account {

    @XdStorageObjectId
    private String id;

    private String owner;

    private Long balance;

    public Account() {
    }

    public Account(final String id, final String owner, final Long balance) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(final Long balance) {
        this.balance = balance;
    }

    @Override
    public String toString() {
        return "Account{" + id + ", " + owner + ", " + balance + "}";
    }
}
