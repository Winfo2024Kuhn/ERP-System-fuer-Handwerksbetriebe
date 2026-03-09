package org.example.kalkulationsprogramm.service;

import java.util.List;

/**
 * Result object for price update operations.
 */
public record PriceUpdateResult(
        List<OfferItem> updated,
        List<OfferItem> skipped,
        List<OfferItem> unmatched
) {
}

