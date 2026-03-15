import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { MainLayout } from './components/layout/MainLayout';
import { ErrorBoundary } from './components/ErrorBoundary';
import { ToastProvider } from './components/ui/toast';
import { ConfirmProvider } from './components/ui/confirm-dialog';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin, RequireAuth } from './auth/RouteGuards';

import TextbausteinEditor from './pages/TextbausteinEditor';
import Leistungseditor from './pages/Leistungseditor';
import Kundeneditor from './pages/Kundeneditor';
import LieferantenEditor from './pages/LieferantenEditor';
import ArtikelEditor from './pages/ArtikelEditor';
import ArbeitsgangEditor from './pages/ArbeitsgangEditor';
import ProduktkategorieEditor from './pages/ProduktkategorieEditor';
import ProjektEditor from './pages/ProjektEditor';
import AnfrageEditor from './pages/AnfrageEditor';
import BestellungenUebersicht from './pages/BestellungenUebersicht';
import ErfolgsanalyseEditor from './pages/ErfolgsanalyseEditor';
import FormularwesenEditor from './pages/FormularwesenEditor';
import OffenePostenEditor from './pages/OffenePostenEditor';
import EmailCenter from './pages/EmailCenter';

import MietabrechnungEditor from './pages/MietabrechnungEditor';
import BenutzerEditor from './pages/BenutzerEditor';
import MitarbeiterEditor from './pages/MitarbeiterEditor';
import ZeiterfassungKalender from './pages/ZeiterfassungKalender';
import ZeiterfassungAuswertung from './pages/ZeiterfassungAuswertung';
import ZeiterfassungZeitkonten from './pages/ZeiterfassungZeitkonten';
import ZeiterfassungFeiertage from './pages/ZeiterfassungFeiertage';
import ZeiterfassungSteuerberater from './pages/ZeiterfassungSteuerberater';
import Urlaubsantraege from './pages/Urlaubsantraege';
import AbteilungBerechtigungenEditor from './pages/AbteilungBerechtigungenEditor';
import TerminKalender from './pages/TerminKalender';
import RechnungsuebersichtEditor from './pages/RechnungsuebersichtEditor';
import FirmaEditor from './pages/FirmaEditor';
import BestellungEditor from './pages/BestellungEditor';
import DocumentEditorPage from './pages/DocumentEditorPage';
import ArbeitszeitartEditor from './pages/ArbeitszeitartEditor';
import EinstellungenEditor from './pages/EinstellungenEditor';
import LoginPage from './pages/LoginPage';
import FirstLoginSetupPage from './pages/FirstLoginSetupPage';

export default function App() {
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              {/* Public pages */}
              <Route path="/login" element={<ErrorBoundary><LoginPage /></ErrorBoundary>} />

              {/* Fullscreen pages outside MainLayout */}
              <Route path="/dokument-editor" element={<ErrorBoundary><DocumentEditorPage /></ErrorBoundary>} />
              <Route path="/onboarding" element={<RequireAdmin><ErrorBoundary><FirstLoginSetupPage /></ErrorBoundary></RequireAdmin>} />

              <Route element={<RequireAuth><MainLayout /></RequireAuth>}>
                <Route path="/" element={<Navigate to="/projekte" replace />} />

                <Route path="/textbausteine" element={<ErrorBoundary><TextbausteinEditor /></ErrorBoundary>} />
                <Route path="/leistungen" element={<ErrorBoundary><Leistungseditor /></ErrorBoundary>} />
                <Route path="/arbeitszeitarten" element={<ErrorBoundary><ArbeitszeitartEditor /></ErrorBoundary>} />
                <Route path="/kunden" element={<ErrorBoundary><Kundeneditor /></ErrorBoundary>} />
                <Route path="/mitarbeiter" element={<ErrorBoundary><MitarbeiterEditor /></ErrorBoundary>} />
                <Route path="/lieferanten" element={<ErrorBoundary><LieferantenEditor /></ErrorBoundary>} />
                <Route path="/artikel" element={<ErrorBoundary><ArtikelEditor /></ErrorBoundary>} />
                <Route path="/arbeitsgaenge" element={<ErrorBoundary><ArbeitsgangEditor /></ErrorBoundary>} />
                <Route path="/produktkategorien" element={<ErrorBoundary><ProduktkategorieEditor /></ErrorBoundary>} />
                <Route path="/projekte" element={<ErrorBoundary><ProjektEditor /></ErrorBoundary>} />
                <Route path="/anfragen" element={<ErrorBoundary><AnfrageEditor /></ErrorBoundary>} />
                <Route path="/bestellungen" element={<ErrorBoundary><BestellungenUebersicht /></ErrorBoundary>} />
                <Route path="/bestellungen/bedarf" element={<ErrorBoundary><BestellungEditor /></ErrorBoundary>} />
                <Route path="/kalender" element={<ErrorBoundary><TerminKalender /></ErrorBoundary>} />
                <Route path="/analyse" element={<ErrorBoundary><ErfolgsanalyseEditor /></ErrorBoundary>} />
                <Route path="/formulare" element={<ErrorBoundary><FormularwesenEditor /></ErrorBoundary>} />
                <Route path="/offeneposten" element={<ErrorBoundary><OffenePostenEditor /></ErrorBoundary>} />
                <Route path="/rechnungsuebersicht" element={<ErrorBoundary><RechnungsuebersichtEditor /></ErrorBoundary>} />
                <Route path="/emails" element={<ErrorBoundary><EmailCenter /></ErrorBoundary>} />

                <Route path="/miete" element={<ErrorBoundary><MietabrechnungEditor /></ErrorBoundary>} />
                <Route path="/benutzer" element={<RequireAdmin><ErrorBoundary><BenutzerEditor /></ErrorBoundary></RequireAdmin>} />

                {/* Zeiterfassung & Admin */}
                <Route path="/zeitbuchungen" element={<ErrorBoundary><ZeiterfassungKalender /></ErrorBoundary>} />
                <Route path="/auswertung" element={<ErrorBoundary><ZeiterfassungAuswertung /></ErrorBoundary>} />
                <Route path="/steuerberater" element={<ErrorBoundary><ZeiterfassungSteuerberater /></ErrorBoundary>} />
                <Route path="/zeitkonten" element={<ErrorBoundary><ZeiterfassungZeitkonten /></ErrorBoundary>} />
                <Route path="/feiertage" element={<ErrorBoundary><ZeiterfassungFeiertage /></ErrorBoundary>} />
                <Route path="/urlaubsantraege" element={<ErrorBoundary><Urlaubsantraege /></ErrorBoundary>} />
                <Route path="/abteilung-berechtigungen" element={<RequireAdmin><ErrorBoundary><AbteilungBerechtigungenEditor /></ErrorBoundary></RequireAdmin>} />
                <Route path="/firma" element={<RequireAdmin><ErrorBoundary><FirmaEditor /></ErrorBoundary></RequireAdmin>} />
                <Route path="/einstellungen" element={<RequireAdmin><ErrorBoundary><EinstellungenEditor /></ErrorBoundary></RequireAdmin>} />

                <Route path="*" element={<Navigate to="/projekte" replace />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}
