package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "uom")
public class Uom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uom_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Uom() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }
}
