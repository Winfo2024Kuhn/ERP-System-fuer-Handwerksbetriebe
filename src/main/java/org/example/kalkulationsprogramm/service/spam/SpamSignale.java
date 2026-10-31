package org.example.kalkulationsprogramm.service.spam;

import java.util.EnumSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;

/**
 * Erkennt die {@link SpamSignal Spam-Signale} einer Mail. Reine Logik ohne Datenbank, damit
 * Regel-Score ({@code SpamFilterService}) und Lernfilter ({@code SpamBayesService}) dieselben
 * Signale sehen.
 */
public final class SpamSignale {

    private SpamSignale() {
    }

    /**
     * Alle Signale, die auf die Mail zutreffen. Nie {@code null}.
     */
    public static Set<SpamSignal> erkenne(Email email) {
        // Wird in Paket „Erkennung“ gefüllt.
        return EnumSet.noneOf(SpamSignal.class);
    }
}
