package quickstart;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/**
 * A plain JavaBean — this class IS the schema. No mapping file, no table.
 *
 * <p>Requirements (see docs/EMBEDDING.md § Codegen requirements): a public no-arg
 * constructor, getters/setters per field, and the class must be TOP-LEVEL (the engine
 * generates wrapper classes from it at runtime).
 *
 * <p>The search index declares which fields you filter on: {@code by_country} answers
 * {@code country EQUALS 'DE'}-shaped queries through the secondary index instead of a scan.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(indexName = "by_country", indexFieldNames = {"country"})
public class Customer {

    @XdStorageObjectId
    private String id;

    private String name;

    private String country;

    public Customer() {
    }

    public Customer(final String id, final String name, final String country) {
        this.id = id;
        this.name = name;
        this.country = country;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(final String country) {
        this.country = country;
    }

    @Override
    public String toString() {
        return "Customer{" + id + ", " + name + ", " + country + "}";
    }
}
