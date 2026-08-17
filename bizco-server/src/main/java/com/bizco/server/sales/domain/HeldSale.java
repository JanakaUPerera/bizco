package com.bizco.server.sales.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A held POS cart snapshot (DomainModel.md, DatabaseDesign.md &sect;12, StateMachines.md &sect;7).
 * {@code customerId}/{@code cashierId}/{@code convertedInvoiceId} are plain foreign keys, not JPA
 * associations, following the same cross-module boundary rule as {@link Invoice}.
 *
 * <p>A held sale is a pre-checkout snapshot only: holding never creates a DRAFT invoice, and it
 * carries no tax calculation - {@link HeldSaleItem} stores just product/qty/price/discount. The
 * two-stage contract (ApiContracts.md &sect;16.6) is: {@code convert} builds a real DRAFT invoice
 * from these items and links it via {@code convertedInvoiceId}, but the held sale only reaches
 * {@link HeldSaleStatus#CONVERTED} when that invoice's own {@code POST /invoices/{id}/post}
 * commits - see {@code PostSaleService}.
 *
 * <p><b>Known gap</b> (same shape as {@code PostSaleService}'s stock gap): holding is documented
 * to reserve stock (SALE-HOLD-001/002/003/CON-001, DatabaseDesign.md &sect;15.5
 * {@code v_reserved_stock}) but the stock ledger (Week 12) does not exist yet, so no reservation
 * is actually enforced here - holding never fails for insufficient stock today. Must be closed
 * when the ledger lands.
 */
@Entity
@Table(name = "held_sales")
public class HeldSale {

    @Id
    @GeneratedValue
    @Column(name = "held_sale_id")
    private UUID id;
    @Column(name = "held_number", nullable = false, length = 30)
    private String heldNumber;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HeldSaleStatus status = HeldSaleStatus.HELD;
    @Column(name = "held_at")
    private Instant heldAt = Instant.now();
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "converted_invoice_id")
    private UUID convertedInvoiceId;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Version
    private long version;

    @OneToMany(mappedBy = "heldSale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HeldSaleItem> items = new ArrayList<>();

    protected HeldSale() {
    }

    public HeldSale(final String heldNumber, final UUID customerId, final UUID cashierId, final String notes,
                    final Instant expiresAt) {
        this.heldNumber = heldNumber;
        this.customerId = customerId;
        this.cashierId = cashierId;
        this.notes = notes;
        this.expiresAt = expiresAt;
    }

    public void addItem(final HeldSaleItem item) {
        item.assignTo(this);
        items.add(item);
    }

    /** StateMachines.md &sect;7.4/7.5: resuming, or re-holding an already-resumed cart, both land on an active state; the caller (service layer) decides which status name applies. */
    public void resume() {
        assertActive();
        status = HeldSaleStatus.RESUMED;
    }

    /**
     * ApiContracts.md &sect;16.4: replaces the cart wholesale and refreshes the reservation window.
     * Clears any earlier {@code convertedInvoiceId} link - that draft invoice was built from the
     * cart as it stood before this edit, so it no longer represents this held sale.
     */
    public void replaceItems(final List<HeldSaleItem> newItems, final UUID customerId, final String notes,
                             final Instant expiresAt) {
        assertActive();
        items.clear();
        newItems.forEach(this::addItem);
        this.customerId = customerId;
        this.notes = notes;
        this.expiresAt = expiresAt;
        this.convertedInvoiceId = null;
        status = HeldSaleStatus.HELD;
    }

    public void cancel() {
        assertActive();
        status = HeldSaleStatus.CANCELLED;
    }

    /** ApiContracts.md &sect;16.6 stage 1: links the DRAFT invoice built from this cart; status stays HELD/RESUMED until that invoice posts. */
    public void linkConvertedInvoice(final UUID invoiceId) {
        assertActive();
        this.convertedInvoiceId = invoiceId;
    }

    /** StateMachines.md &sect;7.6: only called by the invoice posting transaction, once it commits. */
    public void markConverted() {
        if (convertedInvoiceId == null) {
            throw new IllegalStateException("Held sale has no linked invoice to convert to");
        }
        status = HeldSaleStatus.CONVERTED;
    }

    public void expire() {
        assertActive();
        status = HeldSaleStatus.EXPIRED;
    }

    private void assertActive() {
        if (status != HeldSaleStatus.HELD && status != HeldSaleStatus.RESUMED) {
            throw new IllegalStateException("Held sale is not active");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getHeldNumber() {
        return heldNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCashierId() {
        return cashierId;
    }

    public HeldSaleStatus getStatus() {
        return status;
    }

    public Instant getHeldAt() {
        return heldAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getConvertedInvoiceId() {
        return convertedInvoiceId;
    }

    public String getNotes() {
        return notes;
    }

    public long getVersion() {
        return version;
    }

    public List<HeldSaleItem> getItems() {
        return List.copyOf(items);
    }
}
