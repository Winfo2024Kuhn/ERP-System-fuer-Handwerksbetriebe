package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stoesst die Preisuebernahme an, sobald die Dokumentanalyse committet ist.
 *
 * <p>Vorher rief die Analyse den {@link PreisUebernahmeService} mitten in ihrem
 * Lauf direkt auf, in einer zweiten Transaktion nebenher. Rollte die Analyse
 * danach zurueck, stand im Preisverlauf ein Stand fuer einen Beleg, den es in
 * der Datenbank gar nicht gibt. Nach dem Commit gibt es dieses Problem nicht
 * mehr.
 *
 * <p><b>Warum eine eigene Klasse.</b> Die Transaktionsgrenze muss <i>innerhalb</i>
 * des {@code try} liegen. Trueg diese Methode selbst {@code @Transactional}, saesse
 * der Commit im {@code TransactionInterceptor} - also ausserhalb des Rumpfs und
 * damit ausserhalb des Fangnetzes. Das ist keine Spitzfindigkeit: JPA schreibt
 * beim {@code save()} nicht zwingend sofort, sondern flusht beim Commit.
 * Datenbankfehler entstehen deshalb typischerweise genau dort. Sie schluegen
 * ueber {@code triggerAfterCommit} bis zum Aufrufer von
 * {@code analysiereDokument} durch - der Anwender saehe HTTP 500, obwohl sein
 * Dokument sauber gespeichert ist. Zweitens wuerde ein {@code catch} innerhalb
 * der Transaktion diese <i>committen</i> statt zurueckzurollen, sodass
 * Teilschreibungen eines gescheiterten Laufs stehen blieben.
 *
 * <p>Der Aufruf geht deshalb ueber die Bean-Grenze an
 * {@link PreisUebernahmeService#uebernehmePreiseInNeuerTransaktion}, damit der
 * Transaktions-Proxy ueberhaupt greift; ein Aufruf im selben Objekt haette ihn
 * umgangen. Dasselbe Muster nutzt
 * {@code org.example.kalkulationsprogramm.event.EmailBackfillEventListener}.
 *
 * <p><b>Zugestellt wird nur mit laufender Transaktion.</b> Ohne sie verwirft
 * Spring das Event stillschweigend ({@code fallbackExecution} bleibt aus) - eine
 * Preisuebernahme ohne den zugehoerigen Beleg waere genau das, was dieser Umbau
 * abstellt.
 */
@Slf4j
@Component
@AllArgsConstructor
public class PreisUebernahmeEventListener {

    private final PreisUebernahmeService preisUebernahmeService;

    /**
     * Uebernimmt die Preise des Belegs, nachdem dessen Analyse committet ist.
     *
     * <p>Eine Ausnahme bleibt hier und landet im Log: Nach dem Commit ist der
     * Aufrufer laengst fertig, es gibt niemanden mehr, der sie sinnvoll behandeln
     * koennte. Gefangen wird {@code RuntimeException | Error} - dieselbe
     * Kombination, die Spring in
     * {@code TransactionalApplicationListenerSynchronization} weiterreicht und die
     * von dort ungeschuetzt bis zum Aufrufer durchschlaegt.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void beiDokumentAnalyse(PreisUebernahmeEvent event) {
        if (event == null) {
            return;
        }
        try {
            preisUebernahmeService.uebernehmePreiseInNeuerTransaktion(event.lieferant(), event.quelle(),
                    event.dokumentDatum(), event.belegnummer(), event.positionen());
        } catch (RuntimeException | Error e) {
            log.error("Preisuebernahme nach der Dokumentanalyse fehlgeschlagen (Beleg {}): {}",
                    event.belegnummer(), e.toString(), e);
        }
    }
}
