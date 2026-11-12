package org.example.kalkulationsprogramm.service.vorschau;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Begrenzt, wie viele Bild- oder PDF-Umrechnungen gleichzeitig laufen. Jede Umrechnung dekodiert ein Foto bzw. rendert
 * eine Seite im Arbeitsspeicher; ohne Grenze könnten viele gleichzeitige Abrufe (Galerie, Kachelansicht) den Heap
 * sprengen. Wer keinen Platz bekommt, wartet höchstens {@code wartezeitMs} und bekommt dann
 * {@link UmrechnungAusgelastetException} – Server-Threads hängen so nie unbegrenzt an der Sperre.
 *
 * <p>Jeder Dienst hält seine eigene Instanz mit eigenen Werten (z. B. {@code BildVorschauService} für die
 * Anzeigegröße, die Firmendokumente für ihre Vorschaubilder). Die Plätze werden fair vergeben (wer zuerst wartet,
 * kommt zuerst dran).</p>
 */
public class Umrechnungsplaetze {

    /** Eine Umrechnung; darf eine geprüfte Ausnahme werfen (z. B. {@code IOException} beim Lesen). */
    @FunctionalInterface
    public interface Umrechnung<T, E extends Exception> {
        T rechne() throws E;
    }

    private final Semaphore plaetze;
    private final long wartezeitMs;

    /**
     * @param parallel    höchstens so viele Umrechnungen gleichzeitig ({@code 0} = keine, nur für Überlast-Tests)
     * @param wartezeitMs so lange wartet ein Aufruf höchstens auf einen freien Platz
     */
    public Umrechnungsplaetze(int parallel, long wartezeitMs) {
        this.plaetze = new Semaphore(parallel, true);
        this.wartezeitMs = wartezeitMs;
    }

    /**
     * Führt die Umrechnung auf einem freien Platz aus und gibt den Platz danach immer wieder frei.
     *
     * @throws UmrechnungAusgelastetException wenn innerhalb der Wartezeit kein Platz frei wurde (oder das Warten
     *                                        unterbrochen wurde); die Umrechnung läuft dann gar nicht erst
     * @throws E                              was die Umrechnung selbst wirft
     */
    public <T, E extends Exception> T belege(Umrechnung<T, E> umrechnung) throws E {
        try {
            if (!plaetze.tryAcquire(wartezeitMs, TimeUnit.MILLISECONDS)) {
                throw new UmrechnungAusgelastetException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UmrechnungAusgelastetException();
        }
        try {
            return umrechnung.rechne();
        } finally {
            plaetze.release();
        }
    }
}
