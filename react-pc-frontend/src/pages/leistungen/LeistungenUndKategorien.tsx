import { useCallback, useState } from 'react';
import { PageLayout } from '../../components/layout/PageLayout';
import { Card } from '../../components/ui/card';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { LadeAnzeige } from '../../components/ui/lade-anzeige';
import { useNavigationsSperre } from '../../hooks/useNavigationsSperre';
import { Fehlerkarte } from './AuswertungReiter';
import { InfoSeite } from './InfoTexte';
import { LeistungenDialoge, type DialogZustand } from './LeistungenDialoge';
import { OrdnerBaum } from './OrdnerBaum';
import { OrdnerDetail } from './OrdnerDetail';
import { UebersichtAuswertung } from './UebersichtAuswertung';
import { useLeistungenDaten } from './useLeistungenDaten';
import { useLeistungenLoeschen } from './useLeistungenLoeschen';
import { useLeistungenZiehen } from './useLeistungenZiehen';
import { ordnerAusSuche, useOrdnerAuswahl } from './useOrdnerAuswahl';
import { useOrdnerAktionen } from './useOrdnerAktionen';

/**
 * Seite „Leistungen & Kategorien“ (`/leistungen`).
 *
 * Eine Seite für zwei Sichten auf dieselben Daten: Die Ordner der Leistungen
 * sind zugleich die Projekt-Kategorien. Links der Ordnerbaum, rechts der
 * gewählte Ordner mit den Reitern Leistungen, Ordner und Auswertung – oder ohne
 * Auswahl die Übersicht aller Hauptordner. Gewählter Ordner und Reiter stehen
 * in der Adresse (`?ordner=12&reiter=auswertung`), damit Zurück und Links gehen.
 *
 * Die Seite setzt nur zusammen; Laden, Auswahl, Ordner-Aktionen, Verschieben
 * und Löschen stecken in den `use…`-Hooks daneben.
 */
export default function LeistungenUndKategorien() {
    const bestaetigen = useConfirm();
    const daten = useLeistungenDaten();
    const { baum, zaehler, leistungen, ladeStatus } = daten;
    const auswahl = useOrdnerAuswahl({ baum, ordnerRoh: daten.ordnerRoh, ladeStatus });
    const { gewaehlt, gewaehlterOrdner, waehle, aufklappen } = auswahl;

    const [suchtext, setSuchtext] = useState('');
    const [jahr, setJahr] = useState('');
    const [dialog, setDialog] = useState<DialogZustand>(null);
    const [ungespeichertLeistung, setUngespeichertLeistung] = useState(false);
    const [ungespeichertOrdner, setUngespeichertOrdner] = useState(false);
    const ungespeichert = ungespeichertLeistung || ungespeichertOrdner;

    const aktionen = useOrdnerAktionen({ baum, zaehler, neuLaden: daten.neuLaden, ersetzeOrdner: daten.ersetzeOrdner, waehle, aufklappen });
    const ziehen = useLeistungenZiehen({ baum, zaehler, leistungen, neuLaden: daten.neuLaden, aufklappen, uebernimmLeistung: daten.uebernimmLeistung });
    const loeschen = useLeistungenLoeschen({
        baum, gewaehlt, waehle, neuLaden: daten.neuLaden, planen: daten.planen,
        entferneOrdner: daten.entferneOrdner, entferneLeistung: daten.entferneLeistung, setDialog,
    });

    // Ungespeichertes nicht stillschweigend verlieren: beim Ordnerwechsel und beim Verlassen der Seite nachfragen.
    const verwerfenFragen = useCallback(() => bestaetigen({
        title: 'Änderungen verwerfen?',
        message: 'Sie haben Änderungen, die noch nicht gespeichert sind. Wenn Sie jetzt wechseln, gehen sie verloren.',
        confirmLabel: 'Verwerfen und wechseln',
        cancelLabel: 'Weiter bearbeiten',
        variant: 'warning',
    }), [bestaetigen]);
    useNavigationsSperre({
        sperren: ({ currentLocation, nextLocation }) => ungespeichert && (
            currentLocation.pathname !== nextLocation.pathname
            || ordnerAusSuche(currentLocation.search) !== ordnerAusSuche(nextLocation.search)
        ),
        entscheiden: verwerfenFragen,
        beimSchliessenWarnen: ungespeichert,
    });

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="Leistungen & Kategorien"
            titelInfo={<InfoSeite />}
            subtitle="Ihre Leistungen in Ordnern – dieselben Ordner ordnen auch Ihre Projekte ein."
        >
            {ladeStatus === 'laedt' && <LadeAnzeige text="Ordner und Leistungen werden geladen …" onErneut={daten.erneutLaden} />}
            {ladeStatus === 'fehler' && (
                <Fehlerkarte text={daten.ladeFehler ?? 'Die Daten konnten nicht geladen werden.'} onErneut={daten.erneutLaden} />
            )}
            {ladeStatus === 'fertig' && (
                <div className="grid items-start gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
                    <Card className="sticky top-4 flex h-[calc(100vh-15rem)] min-h-[26rem] flex-col overflow-hidden border-slate-200 p-0 shadow-sm">
                        <OrdnerBaum
                            baum={baum}
                            zaehler={zaehler}
                            leistungen={leistungen}
                            gewaehlt={gewaehlt}
                            aufgeklappt={auswahl.aufgeklappt}
                            onAufklappen={aufklappen}
                            onWaehlen={(id) => waehle(id)}
                            suchtext={suchtext}
                            onSuchtext={setSuchtext}
                            neuerOrdner={aktionen.neuerOrdner}
                            onNeuerOrdner={aktionen.neuenOrdnerStarten}
                            onNeuerOrdnerAbbrechen={aktionen.neuerOrdnerAbbrechen}
                            onNeuerOrdnerSpeichern={aktionen.ordnerAnlegen}
                            umbenennenId={aktionen.umbenennenId}
                            onUmbenennen={aktionen.umbenennenStarten}
                            onUmbenennenAbbrechen={aktionen.umbenennenAbbrechen}
                            onUmbenennenSpeichern={aktionen.ordnerUmbenennen}
                            onVerschieben={(id) => setDialog({ art: 'ordnerVerschieben', id })}
                            onLoeschen={(id) => void loeschen.ordnerLoeschenStarten(id)}
                            ziehen={ziehen.ziehen}
                            onZiehenStart={ziehen.setZiehen}
                            onZiehenEnde={ziehen.ziehenEnde}
                            ablageErlaubt={ziehen.ablageErlaubt}
                            onAblegen={ziehen.ablegen}
                            onBildAblegen={(id, datei) => void aktionen.bildAblegen(id, datei)}
                            onLeistungOeffnen={auswahl.leistungOeffnen}
                        />
                    </Card>

                    <Card className="min-w-0 space-y-5 border-slate-200 p-5 shadow-sm">
                        {gewaehlterOrdner ? (
                            <OrdnerDetail
                                ordner={gewaehlterOrdner}
                                baum={baum}
                                zaehler={zaehler}
                                leistungen={leistungen}
                                reiter={auswahl.reiter}
                                waehle={waehle}
                                onUnterordner={() => aktionen.neuenOrdnerStarten(gewaehlterOrdner.id)}
                                onUmbenennen={() => aktionen.umbenennenStarten(gewaehlterOrdner.id)}
                                onVerschieben={() => setDialog({ art: 'ordnerVerschieben', id: gewaehlterOrdner.id })}
                                onLoeschen={() => void loeschen.ordnerLoeschenStarten(gewaehlterOrdner.id)}
                                suchtext={suchtext}
                                onSucheLeeren={() => setSuchtext('')}
                                oeffneLeistung={auswahl.oeffneLeistung}
                                onLeistungSpeichern={daten.leistungSpeichern}
                                onLeistungLoeschen={(l) => void loeschen.leistungLoeschenStarten(l)}
                                onLeistungVerschieben={(l) => setDialog({ art: 'leistungVerschieben', id: l.id })}
                                onZiehenStart={ziehen.setZiehen}
                                onZiehenEnde={ziehen.ziehenEnde}
                                onUngespeichertLeistung={setUngespeichertLeistung}
                                onOrdnerSpeichern={(a) => aktionen.ordnerSpeichern(gewaehlterOrdner.id, a)}
                                ungespeichertOrdner={ungespeichertOrdner}
                                onUngespeichertOrdner={setUngespeichertOrdner}
                                jahr={jahr}
                                onJahr={setJahr}
                            />
                        ) : (
                            <>
                                <div>
                                    <h2 className="text-xl font-bold text-slate-900">Alle Ordner</h2>
                                    <p className="text-sm text-slate-500">Wie viel Zeit steckt in welchem Bereich? Wählen Sie einen Ordner für Details.</p>
                                </div>
                                <UebersichtAuswertung jahr={jahr} onJahr={setJahr} onWaehlen={(id) => waehle(id, 'auswertung')} keineOrdner={daten.ordner.length === 0} />
                            </>
                        )}
                    </Card>
                </div>
            )}

            <LeistungenDialoge
                dialog={dialog}
                baum={baum}
                zaehler={zaehler}
                leistungen={leistungen}
                onSchliessen={() => setDialog(null)}
                ordnerVerschieben={ziehen.ordnerVerschieben}
                leistungVerschieben={ziehen.leistungVerschieben}
                ordnerLoeschenMitModus={loeschen.ordnerLoeschenMitModus}
                leistungTrotzdemLoeschen={loeschen.leistungTrotzdemLoeschen}
            />
        </PageLayout>
    );
}
