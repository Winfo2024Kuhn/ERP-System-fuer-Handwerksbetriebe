import type { AusgangsGeschaeftsDokumentTyp } from '../../../types';
import { EmailComposeModal } from '../../EmailComposeModal';
import { DokumentDatumWahl } from '../DokumentDatumWahl';
import { EmailFormatDialog } from '../EmailFormatDialog';
import { EmailValidityDialog } from '../EmailValidityDialog';
import { OnlineAnnahmeDialog } from '../OnlineAnnahmeDialog';
import type { KontextDaten } from '../types';
import { ExportWarningModal } from './ExportWarningModal';
import { PrintOptionsModal } from './PrintOptionsModal';
import type { DokumentVersand } from './useDokumentVersand';

interface VersandDialogeProps {
    versand: DokumentVersand;
    dokumentTyp: AusgangsGeschaeftsDokumentTyp;
    istRechnung: boolean;
    kontextDaten: KontextDaten;
    betreff: string;
    emailBetreff: string;
    projektId?: number;
    anfrageId?: number;
}

/** Alle Dialoge rund um Drucken, PDF herunterladen und E-Mail senden. */
export function VersandDialoge({ versand, dokumentTyp, istRechnung, kontextDaten, betreff, emailBetreff, projektId, anfrageId }: VersandDialogeProps) {
    const { versandDatum, druck, pdfExport, email, mailfenster } = versand;
    const datumWahl = versandDatum.wahlProps && <DokumentDatumWahl {...versandDatum.wahlProps} />;
    const buchtBeimSenden = mailfenster.buchungBeimSenden;

    return (
        <>
            {pdfExport.warnungOffen && (
                <ExportWarningModal
                    onCancel={pdfExport.warnungAbgebrochen}
                    onConfirm={() => { void pdfExport.warnungBestaetigt(); }}
                    loading={pdfExport.laedt}
                    datum={versandDatum.datum}
                />
            )}
            <EmailFormatDialog
                isOpen={pdfExport.formatOffen}
                onClose={pdfExport.formatSchliessen}
                onSelect={format => { void pdfExport.formatGewaehlt(format); }}
                loading={pdfExport.laedt}
                title="PDF exportieren"
                description="Wählen Sie das Format für den Download:"
                pdfLabel="Standard PDF-Datei herunterladen"
                zugferdLabel="PDF mit maschinenlesbaren Rechnungsdaten (EN 16931)"
                loadingText="PDF wird exportiert…"
                datumWahl={datumWahl}
                gesperrtGrund={versandDatum.fehler}
            />
            {druck.offen && (
                <PrintOptionsModal
                    onCancel={druck.schliessen}
                    onConfirm={optionen => { void druck.drucken(optionen); }}
                    allowFinalization={istRechnung}
                    datumWahl={datumWahl}
                    gesperrtGrund={versandDatum.fehler}
                />
            )}
            <EmailFormatDialog
                isOpen={email.formatOffen}
                onClose={email.abbrechen}
                onSelect={format => { void email.formatGewaehlt(format); }}
                loading={email.laedt}
                title={email.entwurf ? 'Entwurf per E-Mail senden' : undefined}
                description={email.entwurf
                    ? 'Das PDF wird mit Wasserzeichen „ENTWURF“ verschickt. Das Dokument bleibt offen und änderbar.'
                    : undefined}
                loadingText={email.entwurf ? 'Entwurfs-PDF wird erstellt…' : undefined}
                datumWahl={datumWahl}
                gesperrtGrund={versandDatum.fehler}
            />
            <OnlineAnnahmeDialog
                isOpen={email.annahmeOffen}
                dokumentBezeichnung={dokumentTyp === 'NACHTRAGSANGEBOT' ? 'Nachtragsangebot' : 'Angebot'}
                onClose={email.abbrechen}
                onWahl={email.annahmeGewaehlt}
            />
            <EmailValidityDialog
                isOpen={email.gueltigkeitOffen}
                onClose={email.abbrechen}
                onConfirm={email.gueltigkeitBestaetigt}
                defaultTage={email.gueltigkeitTage ?? 14}
            />
            <EmailComposeModal
                isOpen={mailfenster.offen}
                onClose={() => {
                    mailfenster.schliessen();
                    email.zuruecksetzen(false);
                }}
                projektId={projektId}
                anfrageId={anfrageId}
                kundeId={kontextDaten.kundeId}
                anfrage={{
                    bauvorhaben: kontextDaten.projektBauvorhaben || betreff || '',
                    kundenName: kontextDaten.kundenName,
                    kundenEmails: kontextDaten.kundenEmails,
                    kundenAnrede: kontextDaten.anrede,
                    kundenAnsprechpartner: kontextDaten.ansprechpartner,
                }}
                initialAttachments={mailfenster.anhaenge}
                // Alles aus dem Dokument-Editor ist ein Ausgangsgeschäftsdokument. Ist ein eigenes
                // Postfach dafür eingerichtet, geht die Mail darüber raus und der Absender steht fest.
                geschaeftsdokument
                initialRecipient={kontextDaten.kundenEmails?.[0]}
                initialSubject={emailBetreff}
                initialBody={mailfenster.text}
                vorDemSenden={buchtBeimSenden ? mailfenster.bucheVorDemSenden : undefined}
                anhangVorschauGesperrt={buchtBeimSenden
                    ? 'Vorschau und Download erst nach dem Senden: Die Rechnung wird beim Senden gebucht.'
                    : undefined}
                pflichtanhangHinweis={buchtBeimSenden
                    ? 'Das Rechnungs-PDF muss angehängt bleiben, denn die Rechnung wird beim Senden gebucht. '
                        + 'Schließen Sie das Fenster und öffnen Sie die E-Mail neu, um es wieder anzuhängen.'
                    : undefined}
                entwurfSpeichern={!buchtBeimSenden && email.abloesenFuerDokumentId === null}
                freigabenAbloesenFuerDokumentId={email.abloesenFuerDokumentId ?? undefined}
                onSuccess={async () => {
                    await mailfenster.gesendet();
                    email.zuruecksetzen(true);
                }}
            />
        </>
    );
}
