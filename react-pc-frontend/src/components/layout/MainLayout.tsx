import { Outlet } from 'react-router-dom';
import { RibbonNavigation } from './RibbonNav';
import { MobileBottomNav } from './MobileBottomNav';
import { KiHilfeChat } from '../KiHilfeChat';

export function MainLayout() {
  return (
    <div className="h-screen bg-slate-50 flex flex-col overflow-hidden">
      {/* Desktop Navigation - hidden on mobile */}
      <div className="hidden md:block">
        <RibbonNavigation />
      </div>

      {/*
        overflow-x-auto statt overflow-x-hidden: ein Ueberstand wird ab jetzt
        sichtbar (Scrollbalken) statt still abgeschnitten. Der Scrollbalken
        selbst ist nur ein Sicherheitsnetz fuer kuenftige Regressionen -- nach
        den Rahmen-/Raster-Fixes (siehe DetailLayout.tsx) soll hier im
        Normalfall gar kein Ueberstand mehr entstehen, und genau das prueft
        die Design-Pruefung (keinHorizontalerUeberlauf misst main, siehe
        e2e/hilfen/design.ts).
      */}
      <main className="flex-1 w-full px-4 md:px-8 pt-4 md:pt-8 pb-20 md:pb-8 overflow-y-auto overflow-x-auto relative">
        <Outlet />
      </main>

      {/* Mobile Bottom Navigation */}
      <MobileBottomNav />

      {/* Global KI-Hilfe Chat */}
      <KiHilfeChat />
    </div>
  );
}
