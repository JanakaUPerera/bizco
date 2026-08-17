package com.bizco.server.sales.domain;

/** StateMachines.md &sect;7.1. */
public enum HeldSaleStatus {
    HELD,
    RESUMED,
    CANCELLED,
    EXPIRED,
    CONVERTED
}
