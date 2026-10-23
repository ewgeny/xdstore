package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.List;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class Order {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private List<OrderLine> lines;

    @XdStorageAutoSave
    private OrderLine[] extraLines;      // ARRAY shape

    @XdStorageAutoSave
    private Delivery delivery;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> lines) { this.lines = lines; }
    public OrderLine[] getExtraLines() { return extraLines; }
    public void setExtraLines(OrderLine[] extraLines) { this.extraLines = extraLines; }
    public Delivery getDelivery() { return delivery; }
    public void setDelivery(Delivery delivery) { this.delivery = delivery; }
}
