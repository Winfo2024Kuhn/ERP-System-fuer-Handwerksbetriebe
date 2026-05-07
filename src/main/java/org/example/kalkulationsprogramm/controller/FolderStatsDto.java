package org.example.kalkulationsprogramm.controller;

import lombok.Data;

@Data
public class FolderStatsDto {
    // Ungelesen-Counts (steuern die Sidebar-Badges)
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

    // Gesamt-Counts pro Ordner – fuer Pagination/Footer im EmailCenter
    private long inboxTotal;
    private long sentTotal;
    private long trashTotal;
    private long unassignedTotal;
    private long spamTotal;
    private long newsletterTotal;
    private long projectTotal;
    private long offerTotal;
    private long supplierTotal;
    private long starredTotal;
}
