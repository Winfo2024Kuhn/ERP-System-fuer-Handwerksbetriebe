import { useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { LadeAnzeige } from '../components/ui/lade-anzeige';
import { TextbausteinBearbeitenDialog } from '../components/textbaustein/TextbausteinBearbeitenDialog';
import { TextbausteinFilterleiste } from '../components/textbaustein/TextbausteinFilterleiste';
import { TextbausteinKarte } from '../components/textbaustein/TextbausteinKarte';
import { KeineTextbausteine, KeineTreffer, LadenFehlgeschlagen } from '../components/textbaustein/TextbausteinLeerzustaende';
import {
    ALLE_DOKUMENTARTEN,
    OHNE_ZUORDNUNG,
    dokumentartBezeichnung,
    dokumentartOptionen,
    durchsuchbarMachen,
    filtereTextbausteine,
    gueltigeDokumentart,
    zaehleDokumentarten,
} from '../components/textbaustein/textbausteinFilter';
import { entwurfAus, kopieVon, neuerEntwurf } from '../components/textbaustein/textbausteinEntwurf';
import type { TextbausteinDto, TextbausteinEntwurf } from '../components/textbaustein/typen';
import { useTextbausteinFilterUrl } from '../components/textbaustein/useTextbausteinFilterUrl';
import { useTextbausteinKataloge } from '../components/textbaustein/useTextbausteinKataloge';
import { useTextbausteine } from '../components/textbaustein/useTextbausteine';

/** Übersicht der Textbausteine als Karten mit Beispieltext; Bearbeiten im Dialog. */
export default function Textbausteine() {
    const { liste, status, erneutLaden, speichern, loeschen } = useTextbausteine();
    const kataloge = useTextbausteinKataloge();
    const filter = useTextbausteinFilterUrl();
    const [entwurf, setEntwurf] = useState<TextbausteinEntwurf | null>(null);
    // Neuer Schlüssel je Öffnen, damit der Dialog jedes Mal frisch startet.
    const [dialogNummer, setDialogNummer] = useState(0);

    const dokumentart = gueltigeDokumentart(filter.dokumentart, kataloge.gruppen);

    const durchsuchbar = useMemo(() => durchsuchbarMachen(liste), [liste]);
    const treffer = useMemo(
        () => filtereTextbausteine(durchsuchbar, { suche: filter.suche, dokumentart }),
        [durchsuchbar, filter.suche, dokumentart],
    );
    const optionen = useMemo(
        () => dokumentartOptionen(kataloge.gruppen, zaehleDokumentarten(liste)),
        [kataloge.gruppen, liste],
    );

    const oeffne = (neu: TextbausteinEntwurf) => {
        setDialogNummer((n) => n + 1);
        setEntwurf(neu);
    };
    const neuAnlegen = () => {
        // dokumentart ist schon gegen die bekannten Gruppen geprüft.
        oeffne(neuerEntwurf(dokumentart !== ALLE_DOKUMENTARTEN && dokumentart !== OHNE_ZUORDNUNG ? dokumentart : undefined));
    };
    const bearbeiten = (baustein: TextbausteinDto) => oeffne(entwurfAus(baustein));
    const kopieren = (baustein: TextbausteinDto) => oeffne(kopieVon(baustein));

    const laedt = status === 'laedt' || kataloge.status === 'laedt';
    const fehlgeschlagen = status === 'fehler' || kataloge.status === 'fehler';
    const allesNeuLaden = () => {
        erneutLaden();
        kataloge.erneutLaden();
    };

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="Textbausteine"
            subtitle="Fertige Texte für Angebote, Rechnungen und Mahnungen. Die rosa Stellen füllt das Programm im Dokument automatisch – hier sehen Sie ein Beispiel."
            actions={(
                <Button size="sm" onClick={neuAnlegen} disabled={laedt}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Neuer Textbaustein
                </Button>
            )}
        >
            {laedt ? (
                <LadeAnzeige text="Textbausteine werden geladen …" onErneut={allesNeuLaden} />
            ) : fehlgeschlagen ? (
                <LadenFehlgeschlagen onErneut={allesNeuLaden} />
            ) : liste.length === 0 ? (
                <KeineTextbausteine onAnlegen={neuAnlegen} />
            ) : (
                <div className="space-y-5">
                    <TextbausteinFilterleiste
                        suche={filter.suche}
                        onSucheChange={filter.setSuche}
                        dokumentart={dokumentart}
                        onDokumentartChange={filter.setDokumentart}
                        optionen={optionen}
                    />
                    <p className="text-sm text-slate-500" aria-live="polite">
                        {treffer.length === liste.length
                            ? `${liste.length} ${liste.length === 1 ? 'Textbaustein' : 'Textbausteine'}`
                            : `${treffer.length} von ${liste.length} Textbausteinen`}
                    </p>
                    {treffer.length === 0 ? (
                        <KeineTreffer beschreibung={keineTrefferText(filter.suche, dokumentart)} onZuruecksetzen={filter.zuruecksetzen} />
                    ) : (
                        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
                            {treffer.map((baustein) => (
                                <TextbausteinKarte
                                    key={baustein.id}
                                    baustein={baustein}
                                    katalog={kataloge.platzhalter}
                                    onBearbeiten={bearbeiten}
                                    onKopieren={kopieren}
                                />
                            ))}
                        </div>
                    )}
                </div>
            )}
            {entwurf && (
                <TextbausteinBearbeitenDialog
                    key={dialogNummer}
                    entwurf={entwurf}
                    katalog={kataloge.platzhalter}
                    gruppen={kataloge.gruppen}
                    onSpeichern={speichern}
                    onLoeschen={loeschen}
                    onSchliessen={() => setEntwurf(null)}
                />
            )}
        </PageLayout>
    );
}

function keineTrefferText(suche: string, dokumentart: string): string {
    const teile: string[] = [];
    if (dokumentart !== ALLE_DOKUMENTARTEN) teile.push(`für „${dokumentartBezeichnung(dokumentart)}“`);
    if (suche.trim()) teile.push(`mit „${suche.trim()}“`);
    return `Keine Textbausteine ${teile.join(' ')}`.trim();
}
