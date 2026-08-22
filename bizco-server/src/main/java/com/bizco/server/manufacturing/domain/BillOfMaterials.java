package com.bizco.server.manufacturing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A finished variant's recipe (DatabaseDesign.md &sect;57.1, SRS.md &sect;6.4.11.1). Exactly one
 *  {@link BillOfMaterials} row can ever exist per {@code finishedVariantId} (DB UNIQUE constraint)
 *  - there is no separate active/inactive history; {@link #isActive()} simply toggles whether the
 *  recipe can currently be used to Produce (Task 9). */
@Entity
@Table(name = "bill_of_materials")
public class BillOfMaterials {

    @Id
    @GeneratedValue
    @Column(name = "bom_id")
    private UUID id;

    @Column(name = "finished_variant_id", nullable = false, unique = true)
    private UUID finishedVariantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "billOfMaterials", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id asc")
    private final List<BomItem> items = new ArrayList<>();

    protected BillOfMaterials() {
    }

    public BillOfMaterials(final UUID finishedVariantId, final String name) {
        this.finishedVariantId = finishedVariantId;
        this.name = name.trim();
    }

    public void rename(final String name, final boolean active) {
        this.name = name.trim();
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void addItem(final BomItem item) {
        item.attachTo(this);
        items.add(item);
        updatedAt = Instant.now();
    }

    public void removeItem(final UUID bomItemId) {
        final boolean removed = items.removeIf(i -> i.getId().equals(bomItemId));
        if (!removed) {
            throw new IllegalArgumentException("BOM item not found: " + bomItemId);
        }
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFinishedVariantId() { return finishedVariantId; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<BomItem> getItems() { return items; }
}
