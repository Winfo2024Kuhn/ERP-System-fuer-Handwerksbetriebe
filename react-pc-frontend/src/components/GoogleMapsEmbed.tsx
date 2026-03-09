import React from 'react';
import { MapPin } from 'lucide-react';

interface GoogleMapsEmbedProps {
    strasse?: string;
    plz?: string;
    ort?: string;
    className?: string; // Allow custom styling
}

const GoogleMapsEmbed: React.FC<GoogleMapsEmbedProps> = ({ strasse, plz, ort, className }) => {
    const addressQuery = [strasse, plz, ort].filter(Boolean).join(', ');

    if (!addressQuery) {
        return (
            <div className={`bg-slate-50 rounded-2xl p-6 text-center text-slate-500 flex flex-col items-center justify-center ${className}`}>
                <MapPin className="w-10 h-10 mb-2 text-slate-300" />
                <span>Keine Adresse</span>
            </div>
        );
    }

    return (
        <div className={`overflow-hidden rounded-2xl border border-slate-100 shadow-inner bg-slate-100 ${className || 'h-64'}`}>
            <iframe
                src={`https://www.google.com/maps?q=${encodeURIComponent(addressQuery)}&output=embed&z=14`}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
                className="w-full h-full border-0"
                title="Kundenadresse"
            />
        </div>
    );
};

export default GoogleMapsEmbed;
