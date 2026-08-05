package com.bizco.server.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_profile")
public class BusinessProfile {

    @Id
    @GeneratedValue
    private UUID id;
    private String businessName;
    private String legalName;
    private String vatRegistrationNumber;
    private String phone;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    @Column(length = 2)
    private String countryCode = "LK";
    @Column(length = 3)
    private String currencyCode = "LKR";
    private String timezone = "Asia/Colombo";
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private UUID createdBy;

    protected BusinessProfile() {
    }

    public BusinessProfile(final String businessName) {
        this.businessName = businessName;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getVatRegistrationNumber() {
        return vatRegistrationNumber;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void update(final String businessName, final String legalName, final String vatRegistrationNumber,
                       final String phone, final String email, final String addressLine1, final String addressLine2,
                       final String city, final String countryCode, final String currencyCode, final String timezone) {
        this.businessName = businessName;
        this.legalName = legalName;
        this.vatRegistrationNumber = vatRegistrationNumber;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.countryCode = blankDefault(countryCode, "LK");
        this.currencyCode = blankDefault(currencyCode, "LKR");
        this.timezone = blankDefault(timezone, "Asia/Colombo");
    }

    private String blankDefault(final String value, final String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
