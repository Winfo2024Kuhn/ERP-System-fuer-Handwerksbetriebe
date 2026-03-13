import { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { cn } from '../../lib/utils';
import {
    Briefcase, Clock, Mail, Package, MoreHorizontal,
    X, FileText, User, Truck, ShoppingCart, FileCheck,
    BarChart3, Euro, Home, Layers, List, Calendar,
    CalendarDays, Plane, Shield, FileJson, ChevronRight
} from 'lucide-react';

interface NavItem {
    name: string;
    href: string;
    icon: React.ComponentType<{ className?: string }>;
}

interface NavSection {
    label: string;
    items: NavItem[];
}

// Primary tabs for bottom bar
const PRIMARY_TABS: NavItem[] = [
    { name: 'Projekte', href: '/projekte', icon: Briefcase },
    { name: 'Zeit', href: '/zeitbuchungen', icon: Clock },
    { name: 'E-Mail', href: '/emails', icon: Mail },
    { name: 'Daten', href: '/kunden', icon: Package },
];

// Quick links for each tab's submenu
const SUBMENU_ITEMS: Record<string, NavItem[]> = {
    '/projekte': [
        { name: 'Projekte', href: '/projekte', icon: Briefcase },
        { name: 'Anfragen', href: '/anfragen', icon: FileCheck },
        { name: 'Bestellungen', href: '/bestellungen', icon: ShoppingCart },
    ],
    '/zeitbuchungen': [
        { name: 'Kalender', href: '/zeitbuchungen', icon: Calendar },
        { name: 'Auswertung', href: '/auswertung', icon: BarChart3 },
        { name: 'Zeitkonten', href: '/zeitkonten', icon: Clock },
        { name: 'Urlaub', href: '/urlaubsantraege', icon: Plane },
    ],
    '/emails': [
        { name: 'E-Mail Center', href: '/emails', icon: Mail },

    ],
    '/kunden': [
        { name: 'Kunden', href: '/kunden', icon: User },
        { name: 'Lieferanten', href: '/lieferanten', icon: Truck },
        { name: 'Artikel', href: '/artikel', icon: Package },
        { name: 'Mitarbeiter', href: '/mitarbeiter', icon: User },
    ],
};

// All other pages for "Mehr" sheet
const MORE_SECTIONS: NavSection[] = [
    {
        label: 'Dokumente',
        items: [
            { name: 'Textvorlagen', href: '/textbausteine', icon: FileText },
            { name: 'Leistungen', href: '/leistungen', icon: List },
            { name: 'Formularwesen', href: '/formulare', icon: FileJson },
        ]
    },
    {
        label: 'Katalog',
        items: [
            { name: 'Arbeitsgänge', href: '/arbeitsgaenge', icon: Clock },
            { name: 'Kategorien', href: '/produktkategorien', icon: Layers },
        ]
    },
    {
        label: 'Finanzen',
        items: [
            { name: 'Offene Posten', href: '/offeneposten', icon: Euro },
            { name: 'Erfolgsanalyse', href: '/analyse', icon: BarChart3 },
            { name: 'Mietabrechnung', href: '/miete', icon: Home },
        ]
    },
    {
        label: 'Einstellungen',
        items: [
            { name: 'Feiertage', href: '/feiertage', icon: CalendarDays },
            { name: 'Benutzer', href: '/benutzer', icon: User },
            { name: 'Dokumentenrechte', href: '/abteilung-berechtigungen', icon: Shield },
        ]
    },
];

export function MobileBottomNav() {
    const location = useLocation();
    const [showMoreSheet, setShowMoreSheet] = useState(false);
    const [activeSubmenu, setActiveSubmenu] = useState<string | null>(null);
    const [lastTap, setLastTap] = useState<{ tab: string; time: number } | null>(null);

    // Find which primary tab is active based on current route
    const getActiveTab = () => {
        // Check if current path matches any submenu item
        for (const [tabHref, items] of Object.entries(SUBMENU_ITEMS)) {
            if (items.some(item => location.pathname === item.href || location.pathname.startsWith(item.href + '/'))) {
                return tabHref;
            }
        }
        // Check "Mehr" sections
        for (const section of MORE_SECTIONS) {
            if (section.items.some(item => location.pathname === item.href || location.pathname.startsWith(item.href + '/'))) {
                return 'more';
            }
        }
        return PRIMARY_TABS[0].href;
    };

    const activeTab = getActiveTab();

    // Close sheets when route changes
    useEffect(() => {
        setShowMoreSheet(false);
        setActiveSubmenu(null);
    }, [location.pathname]);

    // Double-tap to show submenu
    const handleTabTap = (tabHref: string) => {
        const now = Date.now();

        if (lastTap && lastTap.tab === tabHref && now - lastTap.time < 300) {
            // Double-tap: show submenu
            if (SUBMENU_ITEMS[tabHref]) {
                setActiveSubmenu(activeSubmenu === tabHref ? null : tabHref);
            }
            setLastTap(null);
        } else {
            // Single tap: navigate (if different tab) or show submenu directly on active tab
            setLastTap({ tab: tabHref, time: now });
            if (activeTab === tabHref && SUBMENU_ITEMS[tabHref]) {
                setActiveSubmenu(activeSubmenu === tabHref ? null : tabHref);
            }
        }
    };

    return (
        <>
            {/* Submenu Overlay */}
            {activeSubmenu && (
                <div
                    className="fixed inset-0 bg-black/20 backdrop-blur-sm z-40 md:hidden animate-fadeIn"
                    onClick={() => setActiveSubmenu(null)}
                />
            )}

            {/* Submenu Dropdown */}
            {activeSubmenu && SUBMENU_ITEMS[activeSubmenu] && (
                <div className="fixed bottom-20 left-4 right-4 bg-white rounded-2xl shadow-2xl z-50 md:hidden overflow-hidden animate-slideUp border border-slate-100">
                    <div className="p-2">
                        {SUBMENU_ITEMS[activeSubmenu].map((item) => {
                            const isActive = location.pathname === item.href;
                            return (
                                <Link
                                    key={item.href}
                                    to={item.href}
                                    onClick={() => setActiveSubmenu(null)}
                                    className={cn(
                                        "flex items-center gap-3 px-4 py-3 rounded-xl transition-all active:scale-[0.98]",
                                        isActive
                                            ? "bg-rose-50 text-rose-600"
                                            : "text-slate-700 hover:bg-slate-50"
                                    )}
                                >
                                    <div className={cn(
                                        "w-10 h-10 rounded-full flex items-center justify-center transition-colors",
                                        isActive ? "bg-rose-100" : "bg-slate-100"
                                    )}>
                                        <item.icon className="w-5 h-5" />
                                    </div>
                                    <span className="font-medium">{item.name}</span>
                                    {isActive && (
                                        <div className="ml-auto w-2 h-2 rounded-full bg-rose-500" />
                                    )}
                                </Link>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* More Sheet Overlay */}
            {showMoreSheet && (
                <div
                    className="fixed inset-0 bg-black/30 backdrop-blur-sm z-40 md:hidden animate-fadeIn"
                    onClick={() => setShowMoreSheet(false)}
                />
            )}

            {/* More Sheet */}
            {showMoreSheet && (
                <div className="fixed inset-x-0 bottom-0 bg-white rounded-t-3xl shadow-2xl z-50 md:hidden animate-slideUp max-h-[80vh] overflow-hidden">
                    {/* Sheet Header */}
                    <div className="sticky top-0 bg-white/95 backdrop-blur-md px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                        <h2 className="text-lg font-bold text-slate-900">Weitere Bereiche</h2>
                        <button
                            onClick={() => setShowMoreSheet(false)}
                            className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-600 hover:bg-slate-200 transition-colors active:scale-95"
                        >
                            <X className="w-5 h-5" />
                        </button>
                    </div>

                    {/* Sheet Content */}
                    <div className="overflow-y-auto overscroll-contain pb-safe" style={{ maxHeight: 'calc(80vh - 80px)' }}>
                        {MORE_SECTIONS.map((section) => (
                            <div key={section.label} className="px-4 py-3">
                                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider px-2 mb-2">
                                    {section.label}
                                </p>
                                <div className="space-y-1">
                                    {section.items.map((item) => {
                                        const isActive = location.pathname === item.href;
                                        return (
                                            <Link
                                                key={item.href}
                                                to={item.href}
                                                onClick={() => setShowMoreSheet(false)}
                                                className={cn(
                                                    "flex items-center gap-3 px-3 py-3 rounded-xl transition-all active:scale-[0.98]",
                                                    isActive
                                                        ? "bg-rose-50 text-rose-600"
                                                        : "text-slate-700 hover:bg-slate-50"
                                                )}
                                            >
                                                <div className={cn(
                                                    "w-10 h-10 rounded-full flex items-center justify-center shrink-0",
                                                    isActive ? "bg-rose-100" : "bg-slate-100"
                                                )}>
                                                    <item.icon className="w-5 h-5" />
                                                </div>
                                                <span className="font-medium flex-1">{item.name}</span>
                                                <ChevronRight className={cn(
                                                    "w-4 h-4",
                                                    isActive ? "text-rose-400" : "text-slate-300"
                                                )} />
                                            </Link>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Bottom Navigation Bar */}
            <nav className="fixed bottom-0 inset-x-0 z-30 md:hidden">
                {/* Glassmorphism Background */}
                <div className="absolute inset-0 bg-white/80 backdrop-blur-xl border-t border-slate-200/50 shadow-lg" />

                {/* Safe Area Spacer for iOS */}
                <div className="relative flex items-center justify-around px-2 h-16 pb-safe">
                    {PRIMARY_TABS.map((tab) => {
                        const isActive = activeTab === tab.href;
                        const hasSubmenu = !!SUBMENU_ITEMS[tab.href];

                        return (
                            <Link
                                key={tab.href}
                                to={tab.href}
                                onClick={(e) => {
                                    if (isActive && hasSubmenu) {
                                        e.preventDefault();
                                        handleTabTap(tab.href);
                                    }
                                }}
                                className="flex flex-col items-center justify-center flex-1 py-2 relative group"
                            >
                                {/* Active Indicator */}
                                <div className={cn(
                                    "absolute -top-px left-1/2 -translate-x-1/2 w-8 h-1 rounded-full transition-all duration-300",
                                    isActive ? "bg-rose-500 opacity-100 scale-100" : "bg-transparent opacity-0 scale-0"
                                )} />

                                {/* Icon Container */}
                                <div className={cn(
                                    "w-10 h-10 rounded-2xl flex items-center justify-center transition-all duration-200",
                                    isActive
                                        ? "bg-rose-100 text-rose-600 scale-110"
                                        : "text-slate-500 group-active:scale-95 group-active:bg-slate-100"
                                )}>
                                    <tab.icon className="w-5 h-5" />
                                </div>

                                {/* Label */}
                                <span className={cn(
                                    "text-[10px] font-semibold mt-1 transition-colors",
                                    isActive ? "text-rose-600" : "text-slate-500"
                                )}>
                                    {tab.name}
                                </span>

                                {/* Submenu Indicator */}
                                {hasSubmenu && isActive && (
                                    <div className="absolute -bottom-0.5 left-1/2 -translate-x-1/2 flex gap-0.5">
                                        <div className="w-1 h-1 rounded-full bg-rose-300" />
                                        <div className="w-1 h-1 rounded-full bg-rose-300" />
                                        <div className="w-1 h-1 rounded-full bg-rose-300" />
                                    </div>
                                )}
                            </Link>
                        );
                    })}

                    {/* More Button */}
                    <button
                        onClick={() => setShowMoreSheet(!showMoreSheet)}
                        className="flex flex-col items-center justify-center flex-1 py-2 relative group"
                    >
                        {/* Active Indicator */}
                        <div className={cn(
                            "absolute -top-px left-1/2 -translate-x-1/2 w-8 h-1 rounded-full transition-all duration-300",
                            activeTab === 'more' ? "bg-rose-500 opacity-100 scale-100" : "bg-transparent opacity-0 scale-0"
                        )} />

                        {/* Icon Container */}
                        <div className={cn(
                            "w-10 h-10 rounded-2xl flex items-center justify-center transition-all duration-200",
                            activeTab === 'more' || showMoreSheet
                                ? "bg-rose-100 text-rose-600 scale-110"
                                : "text-slate-500 group-active:scale-95 group-active:bg-slate-100"
                        )}>
                            <MoreHorizontal className={cn(
                                "w-5 h-5 transition-transform duration-200",
                                showMoreSheet ? "rotate-90" : ""
                            )} />
                        </div>

                        {/* Label */}
                        <span className={cn(
                            "text-[10px] font-semibold mt-1 transition-colors",
                            activeTab === 'more' || showMoreSheet ? "text-rose-600" : "text-slate-500"
                        )}>
                            Mehr
                        </span>
                    </button>
                </div>
            </nav>
        </>
    );
}
