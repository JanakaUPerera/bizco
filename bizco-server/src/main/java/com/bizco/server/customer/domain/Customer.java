package com.bizco.server.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue
    @Column(name = "customer_id")
    private UUID id;
    @Column(name = "customer_code", nullable = false, updatable = false, length = 20)
    private String customerCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 20)
    private String phone;
    @Column(length = 150)
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    @Column(name = "nic_ciphertext")
    private byte[] nicCiphertext;
    @Column(name = "br_ciphertext")
    private byte[] brCiphertext;
    @Enumerated(EnumType.STRING)
    private CustomerCategory category = CustomerCategory.RETAIL;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    private CustomerStatus status = CustomerStatus.ACTIVE;
    private boolean consentMarketing;
    private boolean consentDataSharing;
    private Instant consentDate;
    @Column(name = "is_anonymized")
    private boolean anonymized;
    private Instant anonymizedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected Customer() {
    }

    public Customer(final String customerCode, final String name, final String phone, final String email,
                    final String addressLine1, final String addressLine2, final String city,
                    final byte[] nicCiphertext, final byte[] brCiphertext, final CustomerCategory category,
                    final BigDecimal creditLimit, final boolean consentMarketing,
                    final boolean consentDataSharing) {
        this.customerCode = customerCode;
        updateProfile(name, phone, email, addressLine1, addressLine2, city, nicCiphertext, brCiphertext,
                category, creditLimit, consentMarketing, consentDataSharing);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void updateProfile(final String name, final String phone, final String email,
                              final String addressLine1, final String addressLine2, final String city,
                              final byte[] nicCiphertext, final byte[] brCiphertext,
                              final CustomerCategory category, final BigDecimal creditLimit,
                              final boolean consentMarketing, final boolean consentDataSharing) {
        if (anonymized && (nicCiphertext != null || brCiphertext != null)) {
            throw new IllegalStateException("Anonymized customer PII cannot be restored.");
        }
        this.name = requireText(name, "name");
        this.phone = requireText(phone, "phone");
        this.email = blankToNull(email);
        this.addressLine1 = blankToNull(addressLine1);
        this.addressLine2 = blankToNull(addressLine2);
        this.city = blankToNull(city);
        this.nicCiphertext = copy(nicCiphertext);
        this.brCiphertext = copy(brCiphertext);
        this.category = category == null ? CustomerCategory.RETAIL : category;
        setCreditLimit(creditLimit == null ? BigDecimal.ZERO : creditLimit);
        this.consentMarketing = consentMarketing;
        this.consentDataSharing = consentDataSharing;
        this.consentDate = (consentMarketing || consentDataSharing) ? Instant.now() : null;
    }

    public void setCreditLimit(final BigDecimal creditLimit) {
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit limit cannot be negative.");
        }
        this.creditLimit = creditLimit;
    }

    public void block() {
        status = CustomerStatus.BLOCKED;
    }

    public void activate() {
        if (anonymized) {
            throw new IllegalStateException("Anonymized customer cannot be reactivated.");
        }
        status = CustomerStatus.ACTIVE;
    }

    public void anonymize(final Instant at) {
        name = "Deleted Customer " + customerCode;
        phone = "0000000000";
        email = null;
        addressLine1 = null;
        addressLine2 = null;
        city = null;
        nicCiphertext = null;
        brCiphertext = null;
        consentMarketing = false;
        consentDataSharing = false;
        consentDate = null;
        anonymized = true;
        anonymizedAt = at == null ? Instant.now() : at;
        status = CustomerStatus.BLOCKED;
    }

    private String requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private byte[] copy(final byte[] value) {
        return value == null ? null : value.clone();
    }

    public UUID getId() { return id; }
    public String getCustomerCode() { return customerCode; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public byte[] getNicCiphertext() { return copy(nicCiphertext); }
    public byte[] getBrCiphertext() { return copy(brCiphertext); }
    public CustomerCategory getCategory() { return category; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public CustomerStatus getStatus() { return status; }
    public boolean isConsentMarketing() { return consentMarketing; }
    public boolean isConsentDataSharing() { return consentDataSharing; }
    public Instant getConsentDate() { return consentDate; }
    public boolean isAnonymized() { return anonymized; }
    public Instant getAnonymizedAt() { return anonymizedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}

