import { mobileOverlayStyle } from './ui/toast'
import { useState, useRef, useEffect, useId } from 'react'
import { createPortal } from 'react-dom'
import { ChevronLeft, ChevronRight, Calendar } from 'lucide-react'

interface MobileDatePickerProps {
    value: string
    onChange: (value: string) => void
    label?: string
    required?: boolean
    min?: string
    max?: string
    id?: string
    name?: string
    disabled?: boolean
    error?: string
    'aria-label'?: string
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

export default function MobileDatePicker({ value, onChange, label, required, min, max, id, name, disabled, error, 'aria-label': ariaLabel }: MobileDatePickerProps) {
    const generatedId = useId()
    const inputId = id ?? generatedId
    const triggerRef = useRef<HTMLButtonElement>(null)
    const popupRef = useRef<HTMLDivElement>(null)
    const validationRef = useRef<HTMLInputElement>(null)
    const [attempted, setAttempted] = useState(false)
    const parseDate = (text: string) => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return null
        const date = new Date(text + 'T12:00:00')
        return Number.isFinite(date.getTime()) && formatLocalDate(date) === text ? date : null
    }
    const validationMessage = !value ? (required ? `Bitte ${label ?? ariaLabel ?? 'Datum'} auswählen.` : '')
        : !parseDate(value) ? 'Bitte ein gültiges Datum auswählen.'
        : (min && value < min) || (max && value > max) ? 'Datum liegt außerhalb des erlaubten Zeitraums.' : ''
    const shownError = error || (attempted ? validationMessage : '')
    useEffect(() => { validationRef.current?.setCustomValidity(validationMessage) }, [validationMessage])
    const close = () => { setIsOpen(false); triggerRef.current?.focus() }
    const [isOpen, setIsOpen] = useState(false)
    const [viewDate, setViewDate] = useState(() => {
        if (parseDate(value)) return parseDate(value)!
        return new Date()
    })

    // Parse value to Date
    const selectedDate = parseDate(value)

    // Format display value
    const formatDisplayDate = (dateStr: string) => {
        if (!dateStr) return ''
        const d = parseDate(dateStr)
        if (!d) return dateStr
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
        close()
    }

    const isDisabled = (date: Date) => {
        const text = formatLocalDate(date)
        return Boolean((min && text < min) || (max && text > max))
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
        <div className="relative w-full">
            {label && (
                <label htmlFor={inputId} className="block text-sm font-medium text-slate-700 mb-1">
                    {label}
                </label>
            )}

            {/* Input Field */}
            <button
                ref={triggerRef} id={inputId} type="button" disabled={disabled}
                aria-label={ariaLabel ?? label ?? 'Datum wählen'} aria-haspopup="dialog" aria-expanded={isOpen}
                aria-required={required} aria-invalid={shownError ? true : undefined} aria-describedby={shownError ? `${inputId}-error` : undefined}
                onClick={() => { if (isOpen) close(); else { setViewDate(parseDate(value) ?? parseDate(min ?? '') ?? new Date()); setIsOpen(true) } }}
                className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-none transition-all bg-white text-left flex items-center justify-between"
            >
                <span className={value ? 'text-slate-900' : 'text-slate-400'}>
                    {value ? formatDisplayDate(value) : 'Datum wählen...'}
                </span>
                <Calendar className="w-5 h-5 text-slate-400" />
            </button>

            <input ref={validationRef} type="text" value={value} name={name} required={required} disabled={disabled} onChange={() => {}} className="sr-only" tabIndex={-1} aria-hidden="true"
                onInvalid={event => { event.preventDefault(); setAttempted(true); triggerRef.current?.focus() }} />
            {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
            {isOpen && !disabled && createPortal(
                <div style={mobileOverlayStyle} className="fixed inset-0 z-[10020] flex items-center justify-center bg-black/40 p-2 backdrop-blur-sm"
                    onClick={event => { if (event.target === event.currentTarget) close() }}
                    onKeyDown={event => {
                        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close() }
                        if (event.key === 'Tab') {
                            const buttons = Array.from(popupRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])
                            const first=buttons[0], last=buttons.at(-1)
                            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
                            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
                        }
                    }}>
                <div ref={popupRef} role="dialog" aria-modal="true" aria-label={label ?? ariaLabel ?? 'Datum wählen'} className="w-full max-w-sm max-h-full overflow-auto bg-white rounded-2xl shadow-xl border border-slate-200 p-3">
                    {/* Header */}
                    <div className="flex items-center justify-between mb-4">
                        <button
                            type="button"
                            autoFocus aria-label="Vorheriger Monat" onClick={prevMonth}
                            className="min-h-11 min-w-11 flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-rose-500 p-2 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                            <ChevronLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <span className="font-semibold text-slate-900">
                            {MONTHS[viewDate.getMonth()]} {viewDate.getFullYear()}
                        </span>
                        <button
                            type="button"
                            aria-label="Nächster Monat" onClick={nextMonth}
                            className="min-h-11 min-w-11 flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-rose-500 p-2 hover:bg-slate-100 rounded-lg transition-colors"
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
                                        aria-label={formatDisplayDate(formatLocalDate(date))}
                                        aria-pressed={isSelected(date)}
                                        onClick={() => !isDisabled(date) && handleDayClick(date)}
                                        disabled={isDisabled(date)}
                                        className={`w-full min-h-[44px] focus:outline-none focus:ring-2 focus:ring-rose-500 rounded-xl text-base font-semibold transition-colors flex items-center justify-center
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
                            disabled={isDisabled(new Date())}
                            onClick={() => {
                                const today = formatLocalDate(new Date())
                                onChange(today)
                                close()
                            }}
                            className="flex-1 min-h-11 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-40 py-2 text-sm font-medium text-rose-600 bg-rose-50 rounded-lg hover:bg-rose-100 transition-colors"
                        >
                            Heute
                        </button>
                        <button
                            type="button"
                            onClick={close}
                            className="flex-1 min-h-11 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-40 py-2 text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors"
                        >
                            Schließen
                        </button>
                    </div>
                </div></div>, document.body
            )}
        </div>
    )
}
