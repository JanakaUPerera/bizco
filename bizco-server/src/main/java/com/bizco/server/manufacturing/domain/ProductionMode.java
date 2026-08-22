package com.bizco.server.manufacturing.domain;

/** SRS.md &sect;6.4.11.3: whether a Produce transaction stocks the finished variant ahead of
 *  demand or only records component consumption for an immediate, made-to-order sale. */
public enum ProductionMode {
    STOCKED,
    MADE_TO_ORDER
}
