import { lazy } from 'react';
import { createBrowserRouter, createRoutesFromElements, RouterProvider, Outlet, Navigate, Route } from 'react-router-dom';
import { MainLayout } from './components/layout/MainLayout';
import { Seite } from './components/layout/Seite';
import { ToastProvider } from './components/ui/toast';
import { ConfirmProvider } from './components/ui/confirm-dialog';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin, RequireAuth } from './auth/RouteGuards';
import { SessionGuard } from './auth/SessionGuard';
import { installSessionInterceptor } from './auth/sessionInterceptor';
import { AnrufFensterHost } from './features/telefon/AnrufFensterHost';
import { BESTELLBEDARF_PFAD, istFreigegeben } from './lib/freigaben';

// Jede Seite ist eine eigene Datei und wird erst beim Öffnen geladen – der erste Aufruf
// (von unterwegs über Tailscale) lädt so nur das Gerüst und die aktuelle Seite.
const Textbausteine = lazy(() => import('./pages/Textbausteine'));
const LeistungenUndKategorien = lazy(() => import('./pages/leistungen/LeistungenUndKategorien'));
const Kundeneditor = lazy(() => import('./pages/Kundeneditor'));
const LieferantenEditor = lazy(() => import('./pages/LieferantenEditor'));
const ArtikelEditor = lazy(() => import('./pages/ArtikelEditor'));
const ArtikelDetail = lazy(() => import('./pages/ArtikelDetail'));
const ArbeitsgangEditor = lazy(() => import('./pages/ArbeitsgangEditor'));
const ProjektEditor = lazy(() => import('./pages/ProjektEditor'));
const AnfrageEditor = lazy(() => import('./pages/AnfrageEditor'));
const BestellungenUebersicht = lazy(() => import('./pages/BestellungenUebersicht'));
const ErfolgsanalyseEditor = lazy(() => import('./pages/ErfolgsanalyseEditor'));
const KostenstellenControllingEditor = lazy(() => import('./pages/KostenstellenControllingEditor'));
const FormularwesenEditor = lazy(() => import('./pages/FormularwesenEditor'));
const OffenePostenEditor = lazy(() => import('./pages/OffenePostenEditor'));
const EmailCenter = lazy(() => import('./pages/EmailCenter'));
const EmailTextvorlagenEditor = lazy(() => import('./pages/EmailTextvorlagenEditor'));
const EmailEinstellungenPage = lazy(() => import('./pages/EmailEinstellungenPage'));
const MietabrechnungEditor = lazy(() => import('./pages/MietabrechnungEditor'));
const BenutzerEditor = lazy(() => import('./pages/BenutzerEditor'));
const MitarbeiterEditor = lazy(() => import('./pages/MitarbeiterEditor'));
const ZeiterfassungKalender = lazy(() => import('./pages/ZeiterfassungKalender'));
const Monatsabschluss = lazy(() => import('./pages/Monatsabschluss'));
const ZeiterfassungAuswertung = lazy(() => import('./pages/ZeiterfassungAuswertung'));
const ZeiterfassungZeitkonten = lazy(() => import('./pages/ZeiterfassungZeitkonten'));
const ZeiterfassungFeiertage = lazy(() => import('./pages/ZeiterfassungFeiertage'));
const ZeiterfassungSteuerberater = lazy(() => import('./pages/ZeiterfassungSteuerberater'));
const Urlaubsantraege = lazy(() => import('./pages/Urlaubsantraege'));
const Langzeitkrankmeldungen = lazy(() => import('./pages/Langzeitkrankmeldungen'));
const TerminKalender = lazy(() => import('./pages/TerminKalender'));
const RechnungsuebersichtEditor = lazy(() => import('./pages/RechnungsuebersichtEditor'));
const BelegeKasseEditor = lazy(() => import('./pages/BelegeKasseEditor'));
const DokumentUebersichtEditor = lazy(() => import('./pages/DokumentUebersichtEditor'));
const FirmaEditor = lazy(() => import('./pages/FirmaEditor'));
const BestellungEditor = lazy(() => import('./pages/BestellungEditor'));
const DocumentEditorPage = lazy(() => import('./pages/DocumentEditorPage'));
const ArbeitszeitartEditor = lazy(() => import('./pages/ArbeitszeitartEditor'));
const EinstellungenEditor = lazy(() => import('./pages/EinstellungenEditor'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const FirstLoginSetupPage = lazy(() => import('./pages/FirstLoginSetupPage'));
const WebsiteEditor = lazy(() => import('./pages/WebsiteEditor'));
const TelefonPage = lazy(() => import('./pages/TelefonPage'));
const BenachrichtigungenPage = lazy(() => import('./pages/BenachrichtigungenPage'));
const IdsWarenkorbAngekommen = lazy(() => import('./pages/IdsWarenkorbAngekommen'));

// Install the global fetch interceptor once at module load time so that
// any HTTP 401 response from a non-auth endpoint triggers a session-expiry event.
installSessionInterceptor();

// Data router preserves the route tree and lets editors await saving before navigation.
const router = createBrowserRouter(createRoutesFromElements(
  <Route element={<><SessionGuard /><Outlet /></>}>

              {/* Public pages */}
              <Route path="/login" element={<Seite vollbild><LoginPage /></Seite>} />

              {/* Fullscreen pages outside MainLayout — still require authentication */}
              <Route path="/dokument-editor" element={<RequireAuth><><Seite vollbild><DocumentEditorPage /></Seite><AnrufFensterHost akteInNeuemTab /></></RequireAuth>} />
              {/* Rücksprung aus dem Lieferanten-Shop (IDS) im Shop-Tab – eigene Vollbild-Seite ohne Menü. */}
              <Route path="/ids/warenkorb-angekommen" element={<RequireAuth><Seite vollbild><IdsWarenkorbAngekommen /></Seite></RequireAuth>} />
              <Route path="/onboarding" element={<RequireAdmin><Seite vollbild><FirstLoginSetupPage /></Seite></RequireAdmin>} />

              <Route element={<RequireAuth><MainLayout /></RequireAuth>}>
                <Route path="/" element={<Navigate to="/projekte" replace />} />

                <Route path="/textbausteine" element={<Seite><Textbausteine /></Seite>} />
                <Route path="/leistungen" element={<Seite><LeistungenUndKategorien /></Seite>} />
                <Route path="/arbeitszeitarten" element={<Seite><ArbeitszeitartEditor /></Seite>} />
                <Route path="/kunden" element={<Seite><Kundeneditor /></Seite>} />
                <Route path="/mitarbeiter" element={<Seite><MitarbeiterEditor /></Seite>} />
                <Route path="/lieferanten" element={<Seite><LieferantenEditor /></Seite>} />
                <Route path="/artikel" element={<Seite><ArtikelEditor /></Seite>} />
                <Route path="/artikel/:id" element={<Seite><ArtikelDetail /></Seite>} />
                <Route path="/arbeitsgaenge" element={<RequireAdmin><Seite><ArbeitsgangEditor /></Seite></RequireAdmin>} />
                {/* Projekt-Kategorien sind die Ordner der Leistungen – eine gemeinsame Seite. */}
                <Route path="/produktkategorien" element={<Navigate to="/leistungen" replace />} />
                <Route path="/projekte" element={<Seite><ProjektEditor /></Seite>} />
                <Route path="/anfragen" element={<Seite><AnfrageEditor /></Seite>} />
                <Route path="/bestellungen" element={<Seite><BestellungenUebersicht /></Seite>} />
                <Route path={BESTELLBEDARF_PFAD} element={istFreigegeben(BESTELLBEDARF_PFAD)
                    ? <Seite><BestellungEditor /></Seite>
                    : <Navigate to="/bestellungen" replace />} />
                <Route path="/kalender" element={<Seite><TerminKalender /></Seite>} />
                <Route path="/analyse" element={<Seite><ErfolgsanalyseEditor /></Seite>} />
                <Route path="/kostenstellen" element={<Seite><KostenstellenControllingEditor /></Seite>} />
                <Route path="/website" element={<RequireAdmin><Seite><WebsiteEditor /></Seite></RequireAdmin>} />
                <Route path="/formulare" element={<RequireAdmin><Seite><FormularwesenEditor /></Seite></RequireAdmin>} />
                <Route path="/offeneposten" element={<Seite><OffenePostenEditor /></Seite>} />
                <Route path="/rechnungsuebersicht" element={<Seite><RechnungsuebersichtEditor /></Seite>} />
                <Route path="/belege-kasse" element={<Seite><BelegeKasseEditor /></Seite>} />
                <Route path="/dokumentuebersicht" element={<Seite><DokumentUebersichtEditor /></Seite>} />
                <Route path="/emails" element={<Navigate to="/emails/inbox" replace />} />
                <Route path="/emails/:folder" element={<Seite><EmailCenter /></Seite>} />
                <Route path="/emails/:folder/:emailId" element={<Seite><EmailCenter /></Seite>} />
                <Route path="/email-textvorlagen" element={<Seite><EmailTextvorlagenEditor /></Seite>} />
                <Route path="/email-einstellungen" element={<Seite><EmailEinstellungenPage /></Seite>} />
                <Route path="/telefon" element={<Navigate to="/telefon/anrufe" replace />} />
                <Route path="/telefon/:reiter" element={<Seite><TelefonPage /></Seite>} />
                {/* Ziel der Glocke – für alle Angemeldeten; was jemand sieht, entscheidet der Server. */}
                <Route path="/benachrichtigungen" element={<Seite><BenachrichtigungenPage /></Seite>} />

                <Route path="/miete" element={<Seite><MietabrechnungEditor /></Seite>} />
                <Route path="/benutzer" element={<RequireAdmin><Seite><BenutzerEditor /></Seite></RequireAdmin>} />

                {/* Zeiterfassung & Admin */}
                <Route path="/monatsabschluss" element={<Seite><Monatsabschluss /></Seite>} />
                <Route path="/zeitbuchungen" element={<Seite><ZeiterfassungKalender /></Seite>} />
                <Route path="/auswertung" element={<Seite><ZeiterfassungAuswertung /></Seite>} />
                <Route path="/steuerberater" element={<Seite><ZeiterfassungSteuerberater /></Seite>} />
                <Route path="/zeitkonten" element={<Seite><ZeiterfassungZeitkonten /></Seite>} />
                <Route path="/feiertage" element={<Seite><ZeiterfassungFeiertage /></Seite>} />
                <Route path="/urlaubsantraege" element={<Seite><Urlaubsantraege /></Seite>} />
                <Route path="/langzeitkrankmeldungen" element={<Seite><Langzeitkrankmeldungen /></Seite>} />
                {/* Die Rechte sind in die Einstellungen umgezogen; alte Links und Lesezeichen landen im Reiter. */}
                <Route path="/abteilung-berechtigungen" element={<Navigate to="/einstellungen#berechtigungen" replace />} />
                <Route path="/firma" element={<RequireAdmin><Seite><FirmaEditor /></Seite></RequireAdmin>} />
                <Route path="/einstellungen" element={<RequireAdmin><Seite><EinstellungenEditor /></Seite></RequireAdmin>} />

                <Route path="*" element={<Navigate to="/projekte" replace />} />
              </Route>

  </Route>
));

export default function App() {
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}
