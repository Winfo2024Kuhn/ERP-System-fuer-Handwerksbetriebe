package org.example.kalkulationsprogramm.dto.Email;

import java.util.List;

/** Auswahl an Mail-IDs für den Papierkorb ({@code {"ids": [12, 13]}}, höchstens 500). */
public record EmailIdsAnfrage(List<Long> ids) {
}
