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

      <main className="flex-1 w-full px-4 md:px-8 pt-4 md:pt-8 pb-20 md:pb-8 overflow-y-auto overflow-x-hidden relative">
        <Outlet />
      </main>

      {/* Mobile Bottom Navigation */}
      <MobileBottomNav />

      {/* Global KI-Hilfe Chat */}
      <KiHilfeChat />
    </div>
  );
}
