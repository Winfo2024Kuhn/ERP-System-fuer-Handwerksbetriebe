import { useState, useRef, useEffect } from 'react'
import { ChevronLeft, ChevronRight, Calendar } from 'lucide-react'

interface MobileDatePickerProps {
    value: string
    onChange: (value: string) => void
    label?: string
    required?: boolean
    min?: string
}

const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
const MONTHS = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
]

// Helper: Format date as local YYYY-MM-DD (without UTC shift)
const formatLocalDate = (date: Date): string => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

export default function MobileDatePicker({ value, onChange, label, required, min }: MobileDatePickerProps) {
    const [isOpen, setIsOpen] = useState(false)
    const [viewDate, setViewDate] = useState(() => {
        if (value) return new Date(value)
        return new Date()
    })
    const containerRef = useRef<HTMLDivElement>(null)

    // Parse value to Date
    const selectedDate = value ? new Date(value) : null
    const minDate = min ? new Date(min) : null

    // Close on outside click
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setIsOpen(false)
            }
        }
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside)
        }
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [isOpen])

    // Format display value
    const formatDisplayDate = (dateStr: string) => {
        if (!dateStr) return ''
        const d = new Date(dateStr)
        return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    }

    // Get days for current month view
    const getDaysInMonth = () => {
        const year = viewDate.getFullYear()
        const month = viewDate.getMonth()

        const firstDay = new Date(year, month, 1)
        const lastDay = new Date(year, month + 1, 0)

        // Adjust for Monday start (0 = Monday, 6 = Sunday)
        let startOffset = firstDay.getDay() - 1
        if (startOffset < 0) startOffset = 6

        const days: (Date | null)[] = []

        // Empty cells for offset
        for (let i = 0; i < startOffset; i++) {
            days.push(null)
        }

        // Days of month
        for (let d = 1; d <= lastDay.getDate(); d++) {
            days.push(new Date(year, month, d))
        }

        return days
    }

    const handleDayClick = (date: Date) => {
        const formatted = formatLocalDate(date)
        onChange(formatted)
        setIsOpen(false)
    }

    const isDisabled = (date: Date) => {
        if (!minDate) return false
        return date < new Date(formatLocalDate(minDate))
    }

    const isSelected = (date: Date) => {
        if (!selectedDate) return false
        return date.toDateString() === selectedDate.toDateString()
    }

    const isToday = (date: Date) => {
        return date.toDateString() === new Date().toDateString()
    }

    const prevMonth = () => {
        setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() - 1, 1))
    }

    const nextMonth = () => {
        setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() + 1, 1))
    }

    return (
        <div ref={containerRef} className="relative w-full">
            {label && (
                <label className="block text-sm font-medium text-slate-700 mb-1">
                    {label}
                </label>
            )}

            {/* Input Field */}
            <button
                type="button"
                onClick={() => setIsOpen(!isOpen)}
                className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-none transition-all bg-white text-left flex items-center justify-between"
            >
                <span className={value ? 'text-slate-900' : 'text-slate-400'}>
                    {value ? formatDisplayDate(value) : 'Datum wählen...'}
                </span>
                <Calendar className="w-5 h-5 text-slate-400" />
            </button>

            {/* Hidden input for form validation */}
            {required && (
                <input
                    type="text"
                    value={value}
                    required
                    readOnly
                    className="sr-only"
                    tabIndex={-1}
                />
            )}

            {/* Calendar Popup - Full width on mobile */}
            {isOpen && (
                <div className="absolute z-50 mt-2 left-0 right-0 bg-white rounded-2xl shadow-xl border border-slate-200 p-4 animate-in fade-in slide-in-from-top-2 duration-200">
                    {/* Header */}
                    <div className="flex items-center justify-between mb-4">
                        <button
                            type="button"
                            onClick={prevMonth}
                            className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                            <ChevronLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <span className="font-semibold text-slate-900">
                            {MONTHS[viewDate.getMonth()]} {viewDate.getFullYear()}
                        </span>
                        <button
                            type="button"
                            onClick={nextMonth}
                            className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                            <ChevronRight className="w-5 h-5 text-slate-600" />
                        </button>
                    </div>

                    {/* Weekday Headers */}
                    <div className="grid grid-cols-7 gap-1 mb-2">
                        {WEEKDAYS.map(day => (
                            <div key={day} className="text-center text-xs font-medium text-slate-500 py-1">
                                {day}
                            </div>
                        ))}
                    </div>

                    {/* Days Grid - Proper click targets */}
                    <div className="grid grid-cols-7 gap-1">
                        {getDaysInMonth().map((date, i) => (
                            <div key={i}>
                                {date ? (
                                    <button
                                        type="button"
                                        onClick={() => !isDisabled(date) && handleDayClick(date)}
                                        disabled={isDisabled(date)}
                                        className={`w-full min-h-[44px] rounded-xl text-base font-semibold transition-colors flex items-center justify-center
                                            ${isSelected(date)
                                                ? 'bg-rose-600 text-white shadow-sm'
                                                : isToday(date)
                                                    ? 'bg-rose-100 text-rose-700 ring-2 ring-rose-300'
                                                    : isDisabled(date)
                                                        ? 'text-slate-300 cursor-not-allowed'
                                                        : 'text-slate-700 hover:bg-slate-100 active:bg-slate-200'
                                            }`}
                                    >
                                        {date.getDate()}
                                    </button>
                                ) : (
                                    <div className="w-full min-h-[44px]" />
                                )}
                            </div>
                        ))}
                    </div>

                    {/* Quick Actions */}
                    <div className="flex gap-2 mt-4 pt-3 border-t border-slate-100">
                        <button
                            type="button"
                            onClick={() => {
                                const today = formatLocalDate(new Date())
                                onChange(today)
                                setIsOpen(false)
                            }}
                            className="flex-1 py-2 text-sm font-medium text-rose-600 bg-rose-50 rounded-lg hover:bg-rose-100 transition-colors"
                        >
                            Heute
                        </button>
                        <button
                            type="button"
                            onClick={() => setIsOpen(false)}
                            className="flex-1 py-2 text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors"
                        >
                            Schließen
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
