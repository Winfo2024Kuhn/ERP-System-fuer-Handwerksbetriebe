package org.example.kalkulationsprogramm.domain;

/**
 * Punchout-Protokoll-Variante eines Lieferanten.
 * <p>
 * Aktuell nur IDS-Connect 2.5 (ZVSHK), das von den meisten deutschen
 * Großhändlern unterstützt wird. OCI 4.0 (SAP) wäre ein zukünftiger
 * Erweiterungspunkt, falls ein Lieferant kein IDS unterstützt.
 */
public enum IdsProtokoll {
    IDS_CONNECT_2_5,
    OCI_4_0
}
