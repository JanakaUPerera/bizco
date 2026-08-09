package com.bizco.server.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "business_profile")
public class BusinessProfile {

    @Id
    @Column(name = "business_profile_id")
    private Short id = 1;
    private String businessName;
    @Column(name = "tin_number")
    private String vatRegistrationNumber;
    private boolean vatRegistered;
    private String phone;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String province;
    private String postalCode;
    private String website;
    private String logoPath;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected BusinessProfile() {
    }

    public BusinessProfile(final String businessName) {
        this.id = 1;
        this.businessName = businessName;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Short getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getLegalName() {
        return businessName;
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
        return "LK";
    }

    public String getCurrencyCode() {
        return "LKR";
    }

    public String getTimezone() {
        return "Asia/Colombo";
    }

    public long getVersion() {
        return version;
    }

    public void update(final String businessName, final String legalName, final String vatRegistrationNumber,
                       final String phone, final String email, final String addressLine1, final String addressLine2,
                       final String city, final String countryCode, final String currencyCode, final String timezone) {
        this.businessName = businessName;
        this.vatRegistrationNumber = vatRegistrationNumber;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
    }
}
