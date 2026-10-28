package org.example.kalkulationsprogramm.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UeberschneidungPushListenerTest {

    @Mock private WebPushService webPushService;
    @InjectMocks private UeberschneidungPushListener listener;

    @Test
    void schicktPushAnsHandyDesMitarbeiters() {
        listener.beiNeuerMeldung(new ZeitbuchungUeberschneidungService.UeberschneidungGemeldet(7L,
                "Überschneidung am 28.10.2026", "Deine Handy-Buchung 10:00–12:00 hatte Vorrang."));

        verify(webPushService).notifyMitarbeiter(7L, "Überschneidung am 28.10.2026",
                "Deine Handy-Buchung 10:00–12:00 hatte Vorrang.", "/zeiterfassung/tagesbuchungen", "ZEIT_UEBERSCHNEIDUNG");
    }
}
