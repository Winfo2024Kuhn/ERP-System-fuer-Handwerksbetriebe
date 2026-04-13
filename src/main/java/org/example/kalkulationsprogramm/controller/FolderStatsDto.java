package org.example.kalkulationsprogramm.controller;

import lombok.Data;

@Data
public class FolderStatsDto {
    private long inboxCount;
    private long sentCount;
    private long trashCount;
    private long unassignedCount;
    private long inquiriesCount; // New field for "Anfragen" folder
    private long spamCount;
    private long newsletterCount;
    private long projectCount;
    private long offerCount;
    private long supplierCount;
    private long starredCount;
}
