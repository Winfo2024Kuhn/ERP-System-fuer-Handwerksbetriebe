package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.KundenZaehler;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.KundenZaehlerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vergabe fortlaufender, eindeutiger Kundennummern.
 * <p>
 * Nutzt einen Singleton-Counter ({@link KundenZaehler}, id=1) mit
 * PESSIMISTIC_WRITE-Lock, damit konkurrierende Aufrufe (z.B. manueller Anlegen
 * via {@code KundeController} parallel zu Funnel-Eingang aus dem
 * {@code AnfrageFunnelService}) sich nicht gegenseitig die gleiche Nummer
 * zuweisen können. Wird die umgebende Transaktion zurückgerollt, geht auch der
 * Counter zurück – keine Lücken in der Nummern­folge.
 */
@Service
@RequiredArgsConstructor
public class KundennummerService {

    private static final long FALLBACK_START = 1000L;

    private final KundeRepository kundeRepository;
    private final KundenZaehlerRepository kundenZaehlerRepository;

    /**
     * Reserviert atomar die nächste Kundennummer und inkrementiert den Counter.
     * MUSS innerhalb der Transaktion aufgerufen werden, in der der neue Kunde
     * gespeichert wird – sonst greift der Rollback-Schutz nicht.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String reserviereNaechsteKundennummer() {
        KundenZaehler zaehler = kundenZaehlerRepository.lockAndGet();
        if (zaehler == null) {
            // Fallback falls die Migration V222 noch nicht gelaufen ist.
            return generiereNaechsteKundennummer();
        }
        long aktuelle = zaehler.getNaechsteNummer();
        zaehler.setNaechsteNummer(aktuelle + 1);
        return String.valueOf(aktuelle);
    }

    /**
     * Reine Vorschau der nächsten Nummer ohne Reservierung – für UI-Hints
     * (z.B. „nächste freie Nummer: 1023") im Anlegen-Dialog. Liefert keine
     * Atomaritäts-Garantie und darf NICHT zum Speichern eines Kunden verwendet
     * werden.
     */
    public String generiereNaechsteKundennummer() {
        return kundeRepository.findMaxKundennummer()
                .map(max -> {
                    try {
                        long val = Long.parseLong(max);
                        return String.valueOf(val + 1);
                    } catch (NumberFormatException e) {
                        return String.valueOf(FALLBACK_START);
                    }
                })
                .orElse(String.valueOf(FALLBACK_START));
    }
}
