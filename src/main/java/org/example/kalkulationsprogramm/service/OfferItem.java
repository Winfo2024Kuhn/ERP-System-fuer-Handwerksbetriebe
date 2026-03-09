package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

/**
 * Simple representation of an offer position with external code, unit and price.
 */
public record OfferItem(String code, String unit, BigDecimal price, String norm, String name) {}
