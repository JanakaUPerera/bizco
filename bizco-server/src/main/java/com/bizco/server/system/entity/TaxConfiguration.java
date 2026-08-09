package com.bizco.server.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tax_configuration")
public class TaxConfiguration {

    @Id
    @Column(name = "tax_configuration_id")
    private Short id = 1;
    private boolean vatEnabled;
    private BigDecimal vatRate = new BigDecimal("18.0000");
    private Instant changedAt = Instant.now();
    @Version
    private long version;

    protected TaxConfiguration() {
    }

    public TaxConfiguration(final boolean vatEnabled, final BigDecimal vatRate) {
        this.id = 1;
        this.vatEnabled = vatEnabled;
        this.vatRate = vatRate;
    }

    public Short getId() {
        return id;
    }

    public boolean isVatEnabled() {
        return vatEnabled;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public long getVersion() {
        return version;
    }

    public void update(final boolean vatEnabled, final BigDecimal vatRate) {
        this.vatEnabled = vatEnabled;
        this.vatRate = vatRate;
        this.changedAt = Instant.now();
    }
}
