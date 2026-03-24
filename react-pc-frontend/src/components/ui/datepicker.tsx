import { useState, useRef, useEffect } from 'react';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';

interface DatePickerProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

const MONTHS = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
];

const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

export function DatePicker({ value, onChange, placeholder = "Datum wählen", className, disabled }: DatePickerProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [currentMonth, setCurrentMonth] = useState(new Date());
    const containerRef = useRef<HTMLDivElement>(null);

    // Parse value to Date
    const selectedDate = value ? new Date(value) : null;

    useEffect(() => {
        if (selectedDate) {
            setCurrentMonth(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1));
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const formatDate = (date: Date): string => {
        return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    };

    const getDaysInMonth = (date: Date): number => {
        return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
    };

    const getFirstDayOfMonth = (date: Date): number => {
        const day = new Date(date.getFullYear(), date.getMonth(), 1).getDay();
        return day === 0 ? 6 : day - 1; // Monday = 0
    };

    const handlePrevMonth = () => {
        setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1));
    };

    const handleNextMonth = () => {
        setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1));
    };

    const handleDateSelect = (day: number) => {
        const newDate = new Date(currentMonth.getFullYear(), currentMonth.getMonth(), day);
        // Format as YYYY-MM-DD for input value
        const year = newDate.getFullYear();
        const month = String(newDate.getMonth() + 1).padStart(2, '0');
        const dayStr = String(day).padStart(2, '0');
        onChange(`${year}-${month}-${dayStr}`);
        setIsOpen(false);
    };

    const isToday = (day: number): boolean => {
        const today = new Date();
        return (
            day === today.getDate() &&
            currentMonth.getMonth() === today.getMonth() &&
            currentMonth.getFullYear() === today.getFullYear()
        );
    };

    const isSelected = (day: number): boolean => {
        if (!selectedDate) return false;
        return (
            day === selectedDate.getDate() &&
            currentMonth.getMonth() === selectedDate.getMonth() &&
            currentMonth.getFullYear() === selectedDate.getFullYear()
        );
    };

    const renderDays = () => {
        const daysInMonth = getDaysInMonth(currentMonth);
        const firstDay = getFirstDayOfMonth(currentMonth);
        const days = [];

        // Empty cells for days before first of month
        for (let i = 0; i < firstDay; i++) {
            days.push(<div key={`empty-${i}`} className="h-8 w-8" />);
        }

        // Days of month
        for (let day = 1; day <= daysInMonth; day++) {
            days.push(
                <button
                    key={day}
                    type="button"
                    onClick={() => handleDateSelect(day)}
                    className={cn(
                        "h-8 w-8 rounded-full text-sm font-medium transition-colors",
                        "hover:bg-rose-100 hover:text-rose-700",
                        isToday(day) && !isSelected(day) && "border border-rose-300 text-rose-600",
                        isSelected(day) && "bg-rose-600 text-white hover:bg-rose-700 hover:text-white"
                    )}
                >
                    {day}
                </button>
            );
        }

        return days;
    };

    return (
        <div className={cn("relative w-full", className)} ref={containerRef}>
            <div
                className={cn(
                    "flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer hover:bg-slate-50 transition-colors",
                    disabled && "opacity-50 cursor-not-allowed hover:bg-white",
                    isOpen && "ring-2 ring-rose-500 ring-offset-2 border-rose-500"
                )}
                onClick={() => !disabled && setIsOpen(!isOpen)}
            >
                <span className={cn("truncate", !selectedDate && "text-slate-500")}>
                    {selectedDate ? formatDate(selectedDate) : placeholder}
                </span>
                <Calendar className="h-4 w-4 opacity-50" />
            </div>

            {isOpen && !disabled && (
                <div className="absolute z-50 mt-1 w-72 rounded-md border border-slate-200 bg-white p-3 shadow-lg animate-in fade-in-0 zoom-in-95">
                    {/* Header with Year and Month navigation */}
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-1">
                            <button
                                type="button"
                                onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear() - 1, currentMonth.getMonth(), 1))}
                                className="p-1 rounded hover:bg-slate-100 transition-colors text-slate-400 hover:text-slate-600"
                                title="Vorheriges Jahr"
                            >
                                <ChevronLeft className="h-3 w-3" />
                                <ChevronLeft className="h-3 w-3 -ml-2" />
                            </button>
                            <button
                                type="button"
                                onClick={handlePrevMonth}
                                className="p-1 rounded hover:bg-slate-100 transition-colors"
                                title="Vorheriger Monat"
                            >
                                <ChevronLeft className="h-4 w-4" />
                            </button>
                        </div>
                        <button
                            type="button"
                            onClick={() => setCurrentMonth(new Date())}
                            className="font-semibold text-slate-900 hover:text-rose-600 transition-colors"
                            title="Zum aktuellen Monat"
                        >
                            {MONTHS[currentMonth.getMonth()]} {currentMonth.getFullYear()}
                        </button>
                        <div className="flex items-center gap-1">
                            <button
                                type="button"
                                onClick={handleNextMonth}
                                className="p-1 rounded hover:bg-slate-100 transition-colors"
                                title="Nächster Monat"
                            >
                                <ChevronRight className="h-4 w-4" />
                            </button>
                            <button
                                type="button"
                                onClick={() => setCurrentMonth(new Date(currentMonth.getFullYear() + 1, currentMonth.getMonth(), 1))}
                                className="p-1 rounded hover:bg-slate-100 transition-colors text-slate-400 hover:text-slate-600"
                                title="Nächstes Jahr"
                            >
                                <ChevronRight className="h-3 w-3" />
                                <ChevronRight className="h-3 w-3 -ml-2" />
                            </button>
                        </div>
                    </div>

                    {/* Weekday headers */}
                    <div className="grid grid-cols-7 gap-1 mb-1">
                        {WEEKDAYS.map(day => (
                            <div key={day} className="h-8 w-8 flex items-center justify-center text-xs font-medium text-slate-500">
                                {day}
                            </div>
                        ))}
                    </div>

                    {/* Days grid */}
                    <div className="grid grid-cols-7 gap-1">
                        {renderDays()}
                    </div>

                    {/* Quick actions */}
                    <div className="mt-3 pt-2 border-t border-slate-100 flex justify-between">
                        <button
                            type="button"
                            onClick={() => {
                                onChange('');
                                setIsOpen(false);
                            }}
                            className="text-xs text-slate-500 hover:text-slate-700"
                        >
                            Löschen
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                const today = new Date();
                                const year = today.getFullYear();
                                const month = String(today.getMonth() + 1).padStart(2, '0');
                                const day = String(today.getDate()).padStart(2, '0');
                                onChange(`${year}-${month}-${day}`);
                                setIsOpen(false);
                            }}
                            className="text-xs text-rose-600 hover:text-rose-700 font-medium"
                        >
                            Heute
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
