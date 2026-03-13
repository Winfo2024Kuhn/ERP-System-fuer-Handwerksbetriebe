import React, { useState } from 'react';
import { Plus, X, Mail, AlertCircle } from 'lucide-react';
import { Button } from './ui/button';

interface EmailListInputProps {
    emails: string[];
    onChange: (emails: string[]) => void;
    kundenEmails?: string[];  // Vom Kunden vorhandene E-Mails (nur zur Anzeige)
    anfrageEmails?: string[]; // Vom Anfrage vorhandene E-Mails (nur zur Anzeige)
    placeholder?: string;
    label?: string;
}

export const EmailListInput: React.FC<EmailListInputProps> = ({
    emails,
    onChange,
    kundenEmails = [],
    anfrageEmails = [],
    placeholder = "E-Mail-Adresse eingeben...",
    label = "E-Mail-Adressen"
}) => {
    const [inputValue, setInputValue] = useState('');
    const [error, setError] = useState<string | null>(null);

    // Basic email validation
    const isValidEmail = (email: string) => {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    };

    // Check for duplicates across all lists
    const isDuplicate = (email: string) => {
        const lowerEmail = email.toLowerCase().trim();
        const allEmails = [...emails, ...kundenEmails, ...anfrageEmails].map(e => e.toLowerCase().trim());
        return allEmails.includes(lowerEmail);
    };

    const handleAddEmail = () => {
        const trimmedEmail = inputValue.trim();

        if (!trimmedEmail) {
            return;
        }

        if (!isValidEmail(trimmedEmail)) {
            setError('Bitte gültige E-Mail-Adresse eingeben');
            return;
        }

        if (isDuplicate(trimmedEmail)) {
            setError('Diese E-Mail-Adresse existiert bereits');
            return;
        }

        onChange([...emails, trimmedEmail]);
        setInputValue('');
        setError(null);
    };

    const handleRemoveEmail = (emailToRemove: string) => {
        onChange(emails.filter(e => e !== emailToRemove));
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleAddEmail();
        }
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setInputValue(e.target.value);
        if (error) setError(null);
    };

    return (
        <div className="space-y-3">
            <label className="block text-sm font-medium text-slate-700">
                <Mail className="w-4 h-4 inline-block mr-1" />
                {label}
            </label>

            {/* Kunden-E-Mails (read-only display) */}
            {kundenEmails.length > 0 && (
                <div className="space-y-1">
                    <p className="text-xs text-slate-500">Vom Kunden:</p>
                    <div className="flex flex-wrap gap-2">
                        {kundenEmails.map((email, idx) => (
                            <span
                                key={`kunde-${idx}`}
                                className="inline-flex items-center gap-1 px-2 py-1 bg-slate-100 text-slate-600 text-sm rounded-lg"
                            >
                                <Mail className="w-3 h-3" />
                                {email}
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {/* Anfrage-E-Mails (read-only display) */}
            {anfrageEmails.length > 0 && (
                <div className="space-y-1">
                    <p className="text-xs text-slate-500">Vom Anfrage:</p>
                    <div className="flex flex-wrap gap-2">
                        {anfrageEmails.filter(e => !kundenEmails.includes(e)).map((email, idx) => (
                            <span
                                key={`anfrage-${idx}`}
                                className="inline-flex items-center gap-1 px-2 py-1 bg-slate-100 text-slate-600 text-sm rounded-lg"
                            >
                                <Mail className="w-3 h-3" />
                                {email}
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {/* Zusätzliche E-Mails (editierbar) */}
            {emails.length > 0 && (
                <div className="space-y-1">
                    <p className="text-xs text-slate-500">Zusätzliche E-Mails:</p>
                    <div className="flex flex-wrap gap-2">
                        {emails.map((email, idx) => (
                            <span
                                key={`added-${idx}`}
                                className="inline-flex items-center gap-1 px-2 py-1 bg-rose-50 text-rose-700 text-sm rounded-lg group"
                            >
                                <Mail className="w-3 h-3" />
                                {email}
                                <button
                                    type="button"
                                    onClick={() => handleRemoveEmail(email)}
                                    className="ml-1 hover:bg-rose-200 rounded-full p-0.5 transition-colors"
                                    title="E-Mail entfernen"
                                >
                                    <X className="w-3 h-3" />
                                </button>
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {/* Input Row */}
            <div className="flex gap-2">
                <div className="flex-1 relative">
                    <input
                        type="email"
                        value={inputValue}
                        onChange={handleInputChange}
                        onKeyDown={handleKeyDown}
                        placeholder={placeholder}
                        className={`w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 ${error ? 'border-red-300 bg-red-50' : 'border-slate-200'
                            }`}
                    />
                </div>
                <Button
                    type="button"
                    size="sm"
                    onClick={handleAddEmail}
                    className="bg-rose-600 text-white hover:bg-rose-700"
                >
                    <Plus className="w-4 h-4" />
                </Button>
            </div>

            {/* Error Message */}
            {error && (
                <div className="flex items-center gap-1 text-red-600 text-sm">
                    <AlertCircle className="w-4 h-4" />
                    {error}
                </div>
            )}
        </div>
    );
};

export default EmailListInput;
