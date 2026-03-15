import { useState, useEffect, useMemo } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { cn } from '../../lib/utils';
import {
    BarChart3, Briefcase, Building2, Clock, Euro, FileCheck, FileJson,
    FileText, Home, Layers, List, Mail, Package, Settings,
    ShoppingCart, Truck, ChevronUp, ChevronDown, User, LogOut,
    Calendar, CalendarDays, Plane, Shield
} from 'lucide-react';
import { Button } from '../ui/button';
import { NotificationBell } from './NotificationBell';
import { useAuth } from '../../auth/AuthContext';

// Navigation structure with subgroups for better organization
interface NavItem {
    name: string;
    href: string;
    icon: React.ComponentType<{ className?: string }>;
}

interface NavSubgroup {
    label: string;
    items: NavItem[];
}

interface NavCategory {
    category: string;
    subgroups: NavSubgroup[];
}

const NAVIGATION: NavCategory[] = [
    {
        category: 'Vorlagen & Stammdaten',
        subgroups: [
            {
                label: 'Dokumente',
                items: [
                    { name: 'Textvorlagen', href: '/textbausteine', icon: FileText },
                    { name: 'Leistungen', href: '/leistungen', icon: List },
                    { name: 'Stundensätze', href: '/arbeitszeitarten', icon: Clock },
                    { name: 'Formularwesen', href: '/formulare', icon: FileJson },
                ]
            },
            {
                label: 'Kontakte',
                items: [
                    { name: 'Kunden', href: '/kunden', icon: User },
                    { name: 'Mitarbeiter', href: '/mitarbeiter', icon: User },
                    { name: 'Lieferanten', href: '/lieferanten', icon: Truck },
                ]
            },
            {
                label: 'Katalog',
                items: [
                    { name: 'Artikel', href: '/artikel', icon: Package },
                    { name: 'Arbeitsgänge', href: '/arbeitsgaenge', icon: Clock },
                    { name: 'Kategorien', href: '/produktkategorien', icon: Layers },
                ]
            },
            {
                label: 'Administration',
                items: [
                    { name: 'Dokumentenrechte', href: '/abteilung-berechtigungen', icon: Shield },
                    { name: 'Firma', href: '/firma', icon: Building2 },
                    { name: 'Einstellungen', href: '/einstellungen', icon: Settings },
                ]
            }
        ]
    },
    {
        category: 'Projektmanagement',
        subgroups: [
            {
                label: 'Aufträge',
                items: [
                    { name: 'Projekte', href: '/projekte', icon: Briefcase },
                    { name: 'Anfragen', href: '/anfragen', icon: FileCheck },
                ]
            },
            {
                label: 'Planung',
                items: [
                    { name: 'Kalender', href: '/kalender', icon: Calendar },
                ]
            },
            {
                label: 'Einkauf',
                items: [
                    { name: 'Bestellungen', href: '/bestellungen', icon: ShoppingCart },
                    { name: 'Bedarf', href: '/bestellungen/bedarf', icon: List },
                ]
            }
        ]
    },
    {
        category: 'Zeiterfassung',
        subgroups: [
            {
                label: 'Übersicht',
                items: [
                    { name: 'Kalender', href: '/zeitbuchungen', icon: Calendar },
                ]
            },
            {
                label: 'Berichte',
                items: [
                    { name: 'Auswertung', href: '/auswertung', icon: BarChart3 },
                    { name: 'Steuerberater', href: '/steuerberater', icon: Mail },
                ]
            },
            {
                label: 'Einstellungen',
                items: [
                    { name: 'Zeitkonten', href: '/zeitkonten', icon: Clock },
                    { name: 'Feiertage', href: '/feiertage', icon: CalendarDays },
                ]
            },
            {
                label: 'Urlaub',
                items: [
                    { name: 'Anträge', href: '/urlaubsantraege', icon: Plane },
                ]
            }
        ]
    },
    {
        category: 'Kommunikation',
        subgroups: [
            {
                label: 'E-Mail',
                items: [
                    { name: 'E-Mail Center', href: '/emails', icon: Mail },
                ]
            },

        ]
    },
    {
        category: 'Finanzen & Controlling',
        subgroups: [
            {
                label: 'Buchhaltung',
                items: [
                    { name: 'Offene Posten', href: '/offeneposten', icon: Euro },
                    { name: 'Rechnungen', href: '/rechnungsuebersicht', icon: FileText },
                    { name: 'Mietabrechnung', href: '/miete', icon: Home },
                ]
            },
            {
                label: 'Auswertung',
                items: [
                    { name: 'Erfolgsanalyse', href: '/analyse', icon: BarChart3 },
                ]
            }
        ]
    }
];

const ADMIN_ONLY_PATHS = new Set(['/abteilung-berechtigungen', '/firma', '/einstellungen', '/benutzer']);


export function RibbonNavigation() {
    const location = useLocation();
    const navigate = useNavigate();
    const { user, isAdmin, logout } = useAuth();

    const visibleNavigation = useMemo<NavCategory[]>(() => {
        return NAVIGATION
            .map((category) => ({
                ...category,
                subgroups: category.subgroups
                    .map((subgroup) => ({
                        ...subgroup,
                        items: subgroup.items.filter((item) => isAdmin || !ADMIN_ONLY_PATHS.has(item.href)),
                    }))
                    .filter((subgroup) => subgroup.items.length > 0),
            }))
            .filter((category) => category.subgroups.length > 0);
    }, [isAdmin]);

    const [activeCategory, setActiveCategory] = useState<string>(NAVIGATION[0].category);
    const [isExpanded, setIsExpanded] = useState(true);

    // Auto-select category based on current route
    useEffect(() => {
        const currentGroup = visibleNavigation.find(group =>
            group.subgroups.some(sg => sg.items.some(item => location.pathname.startsWith(item.href)))
        );
        if (currentGroup) {
            setActiveCategory(currentGroup.category);
        }
    }, [location.pathname, visibleNavigation]);

    useEffect(() => {
        if (visibleNavigation.length === 0) {
            return;
        }
        if (!visibleNavigation.some(group => group.category === activeCategory)) {
            setActiveCategory(visibleNavigation[0].category);
        }
    }, [visibleNavigation, activeCategory]);

    // User Menu State
    const currentUser = user;
    const [showUserMenu, setShowUserMenu] = useState(false);

    const handleLogout = async () => {
        setShowUserMenu(false);
        await logout();
        navigate('/login', { replace: true });
    };


    const toggleRibbon = () => setIsExpanded(!isExpanded);

    return (
        <div className="flex flex-col bg-white border-b border-slate-200 shadow-sm sticky top-0 z-40 transition-all">
            {/* Top Bar: Logo & Tabs */}
            <div className="flex items-center px-4 h-16 border-b border-rose-100 bg-white shadow-sm gap-8">
                {/* Company Logo */}
                <div className="flex items-center shrink-0">
                    <img src="/firmenlogo_icon.png" alt="Company Logo" className="h-10 w-auto object-contain" />
                </div>

                {/* Category Tabs */}
                <div className="flex-1 flex overflow-x-auto overflow-y-hidden no-scrollbar gap-2 h-full items-end">
                    {visibleNavigation.map((group) => (
                        <button
                            key={group.category}
                            onClick={() => {
                                if (activeCategory === group.category) {
                                    toggleRibbon();
                                } else {
                                    setActiveCategory(group.category);
                                    setIsExpanded(true);
                                }
                            }}
                            className={cn(
                                "px-4 py-3 text-sm font-semibold whitespace-nowrap transition-all rounded-t-lg relative bottom-[-1px]",
                                activeCategory === group.category
                                    ? "text-rose-700 bg-rose-50 border-t-2 border-x border-rose-200 border-b-transparent shadow-sm z-10"
                                    : "text-slate-500 hover:text-slate-800 hover:bg-slate-50 border-transparent border-b-2 border-b-transparent mb-[1px]"
                            )}
                        >
                            {group.category}
                        </button>
                    ))}
                </div>

                {/* Notification Bell */}
                <div className="flex items-center ml-2">
                    <NotificationBell />
                </div>

                {/* User Selector */}
                <div className="relative ml-2 pl-4 border-l border-slate-200">
                    <button
                        onClick={() => setShowUserMenu(!showUserMenu)}
                        className="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors group"
                    >
                        <div className="w-8 h-8 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 border border-rose-200">
                            <User className="w-4 h-4" />
                        </div>
                        <div className="text-left hidden md:block">
                            <p className="text-sm font-semibold text-slate-700 group-hover:text-slate-900 line-clamp-1">
                                {currentUser ? currentUser.displayName : "Lade..."}
                            </p>
                            <p className="text-xs text-slate-500">{isAdmin ? 'Administrator' : 'Angemeldet'}</p>
                        </div>
                        <ChevronDown className={cn("w-4 h-4 text-slate-400 transition-transform duration-200", showUserMenu ? "rotate-180" : "")} />
                    </button>

                    {showUserMenu && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setShowUserMenu(false)} />
                            <div className="absolute right-0 top-full mt-2 w-56 bg-white rounded-xl shadow-lg border border-slate-100 py-2 z-50 animate-in fade-in zoom-in-95 duration-200">
                                <div className="px-4 py-2 border-b border-slate-50 mb-2">
                                    <p className="text-sm font-semibold text-slate-700 line-clamp-1">{currentUser?.displayName || 'Benutzer'}</p>
                                    <p className="text-xs text-slate-500">{currentUser?.username || 'Kein Username'}</p>
                                </div>
                                <div className="border-t border-slate-100 mt-2 pt-2 pb-1">
                                    {isAdmin && (
                                        <Link
                                            to="/benutzer"
                                            onClick={() => setShowUserMenu(false)}
                                            className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                                        >
                                            <User className="w-4 h-4" />
                                            Benutzer verwalten
                                        </Link>
                                    )}
                                    <button
                                        onClick={handleLogout}
                                        className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        Abmelden
                                    </button>
                                </div>
                            </div>
                        </>
                    )}
                </div>

                {/* Toggle Button */}
                <Button variant="ghost" size="sm" onClick={toggleRibbon} className="ml-2 text-slate-400">
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </Button>
            </div>

            {/* Ribbon Content (Toolbar) with Subgroups */}
            <div
                className={cn(
                    "overflow-hidden transition-all duration-300 ease-in-out bg-slate-50/50",
                    isExpanded ? "max-h-40 opacity-100 border-b border-slate-200" : "max-h-0 opacity-0"
                )}
            >
                <div className="px-3 py-2 flex gap-1 overflow-x-auto no-scrollbar">
                    {visibleNavigation.find(g => g.category === activeCategory)?.subgroups.map((subgroup, sgIndex) => (
                        <div key={subgroup.label} className="flex items-center">
                            {/* Subgroup Container */}
                            <div className="flex flex-col">
                                {/* Subgroup Label */}
                                <span className="text-[9px] font-semibold text-slate-400 uppercase tracking-wider px-2 mb-1">
                                    {subgroup.label}
                                </span>
                                {/* Subgroup Items */}
                                <div className="flex gap-1">
                                    {subgroup.items.map((item) => {
                                        // Exact match or starts with item.href followed by / (for nested routes)
                                        // But not if another sibling route is a better match
                                        const isExactMatch = location.pathname === item.href;
                                        const isNestedMatch = location.pathname.startsWith(item.href + '/');
                                        // Check if there's a more specific sibling route that matches
                                        const hasBetterSiblingMatch = subgroup.items.some(sibling => 
                                            sibling.href !== item.href && 
                                            sibling.href.startsWith(item.href) && 
                                            (location.pathname === sibling.href || location.pathname.startsWith(sibling.href + '/'))
                                        );
                                        const isActive = (isExactMatch || isNestedMatch) && !hasBetterSiblingMatch;
                                        return (
                                            <Link
                                                key={item.href}
                                                to={item.href}
                                                className={cn(
                                                    "flex flex-col items-center justify-center gap-1 min-w-[4.5rem] p-2 rounded-lg transition-all hover:bg-white hover:shadow-sm hover:scale-105 active:scale-95 group",
                                                    isActive ? "bg-white shadow-sm ring-1 ring-slate-200" : ""
                                                )}
                                            >
                                                <div className={cn(
                                                    "p-2 rounded-full transition-colors",
                                                    isActive ? "bg-rose-100 text-rose-600" : "bg-slate-100 text-slate-500 group-hover:text-rose-600 group-hover:bg-rose-50"
                                                )}>
                                                    <item.icon className="w-5 h-5" />
                                                </div>
                                                <span className={cn(
                                                    "text-[10px] font-medium text-center leading-tight max-w-[4.5rem] truncate",
                                                    isActive ? "text-rose-700" : "text-slate-600"
                                                )}>
                                                    {item.name}
                                                </span>
                                            </Link>
                                        );
                                    })}
                                </div>
                            </div>
                            {/* Separator between subgroups */}
                            {sgIndex < (visibleNavigation.find(g => g.category === activeCategory)?.subgroups.length ?? 0) - 1 && (
                                <div className="w-px h-16 bg-slate-200 mx-3" />
                            )}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}
