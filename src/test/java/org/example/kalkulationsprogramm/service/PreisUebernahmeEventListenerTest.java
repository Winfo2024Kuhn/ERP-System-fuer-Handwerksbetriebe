package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueft das Fangnetz des Listeners.
 *
 * <p>Er laeuft nach dem Commit der Analyse; dort gibt es niemanden mehr, der eine
 * Ausnahme sinnvoll behandeln koennte. Kaeme sie durch, reichte Spring sie ueber
 * {@code triggerAfterCommit} bis zum Aufrufer von {@code analysiereDokument}
 * weiter - der Anwender saehe HTTP 500 fuer ein Dokument, das sauber gespeichert
 * ist.
 *
 * <p>Das Zusammenspiel mit der Transaktion - Zustellung erst nach dem Commit,
 * kein Preisstand nach einem Rollback - deckt {@code PreisUebernahmeNachCommitTest}
 * am echten Spring-Kontext ab.
 */
@ExtendWith(MockitoExtension.class)
class PreisUebernahmeEventListenerTest {

    @Mock
    private PreisUebernahmeService preisUebernahmeService;

    @InjectMocks
    private PreisUebernahmeEventListener listener;

    @Test
    void datenbankfehlerSchlaegtNichtNachAussenDurch() {
        when(preisUebernahmeService.uebernehmePreiseInNeuerTransaktion(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Verbindung weggebrochen"));

        assertDoesNotThrow(() -> listener.beiDokumentAnalyse(event("RE-2026-0099")));
    }

    /**
     * Spring reicht in {@code TransactionalApplicationListenerSynchronization}
     * {@code RuntimeException | Error} weiter - ein {@code Error} aus dem
     * Commit-Flush wuerde also genauso beim Anwender landen.
     */
    @Test
    void auchEinErrorBleibtImListener() {
        when(preisUebernahmeService.uebernehmePreiseInNeuerTransaktion(any(), any(), any(), any(), any()))
                .thenThrow(new StackOverflowError("Mapping-Schleife"));

        assertDoesNotThrow(() -> listener.beiDokumentAnalyse(event("RE-2026-0100")));
    }

    @Test
    void ohneEventPassiertNichts() {
        listener.beiDokumentAnalyse(null);

        verify(preisUebernahmeService, never())
                .uebernehmePreiseInNeuerTransaktion(any(), any(), any(), any(), any());
    }

    private PreisUebernahmeEvent event(String belegnummer) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Musterlieferant Absturz");
        return new PreisUebernahmeEvent(lieferant, PreisQuelle.RECHNUNG, new Date(), belegnummer,
                List.of(new PreisUebernahmeService.Position("EV-2", new BigDecimal("12.00"), "1 C62", "C62")));
    }
}
