package com.bizco.server.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue
    @Column(name = "supplier_id")
    private UUID id;

    @Column(name = "supplier_code", nullable = false, length = 20)
    private String supplierCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "tin_number", length = 30)
    private String tinNumber;

    @Column(name = "payment_terms", length = 255)
    private String paymentTerms;

    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Supplier() {
    }

    public Supplier(final String supplierCode, final String name, final String contactPerson,
                    final String address, final String phone, final String email, final String tinNumber,
                    final String paymentTerms, final BigDecimal openingBalance) {
        update(supplierCode, name, contactPerson, address, phone, email, tinNumber, paymentTerms,
                openingBalance, SupplierStatus.ACTIVE);
    }

    public void update(final String supplierCode, final String name, final String contactPerson,
                       final String address, final String phone, final String email, final String tinNumber,
                       final String paymentTerms, final BigDecimal openingBalance, final SupplierStatus status) {
        this.supplierCode = supplierCode.trim();
        this.name = name.trim();
        this.contactPerson = blankToNull(contactPerson);
        this.address = blankToNull(address);
        this.phone = blankToNull(phone);
        this.email = blankToNull(email);
        this.tinNumber = blankToNull(tinNumber);
        this.paymentTerms = blankToNull(paymentTerms);
        this.openingBalance = openingBalance;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void activate() { this.status = SupplierStatus.ACTIVE; this.updatedAt = Instant.now(); }
    public void deactivate() { this.status = SupplierStatus.INACTIVE; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getSupplierCode() { return supplierCode; }
    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getTinNumber() { return tinNumber; }
    public String getPaymentTerms() { return paymentTerms; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public SupplierStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
