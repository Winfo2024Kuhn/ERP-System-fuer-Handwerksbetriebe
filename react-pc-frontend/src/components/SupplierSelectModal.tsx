import React, { useState, useEffect } from 'react';
import { Search, X } from 'lucide-react';
import { Card } from './ui/card';
import { Input } from './ui/input';

interface SupplierSelectModalProps {
    onSelect: (supplier: { id: number; name: string }) => void;
    onClose: () => void;
}

interface Supplier {
    id: number;
    lieferantenname: string;
}

export const SupplierSelectModal: React.FC<SupplierSelectModalProps> = ({ onSelect, onClose }) => {
    const [searchTerm, setSearchTerm] = useState('');
    const [suppliers, setSuppliers] = useState<Supplier[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const fetchSuppliers = async () => {
            setLoading(true);
            try {
                const res = await fetch('/api/lieferanten?size=1000');
                if (res.ok) {
                    const data = await res.json();
                    setSuppliers(data?.lieferanten && Array.isArray(data.lieferanten) ? data.lieferanten : []);
                }
            } catch (error) {
                console.error('Failed to load suppliers', error);
            } finally {
                setLoading(false);
            }
        };
        fetchSuppliers();
    }, []);

    const filteredSuppliers = suppliers.filter(s =>
        s.lieferantenname?.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-md flex flex-col max-h-[80vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <h3 className="font-semibold text-lg">Lieferant auswählen</h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="p-4 border-b">
                    <div className="relative">
                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                            placeholder="Suchen..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="pl-9"
                            autoFocus
                        />
                    </div>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    {loading ? (
                        <div className="text-center py-4 text-slate-500">Lade...</div>
                    ) : filteredSuppliers.length === 0 ? (
                        <div className="text-center py-4 text-slate-500">Keine Lieferanten gefunden.</div>
                    ) : (
                        <div className="space-y-1">
                            {filteredSuppliers.map((supplier) => (
                                <button
                                    key={supplier.id}
                                    className="w-full text-left px-3 py-2 rounded hover:bg-rose-50 text-sm text-slate-700 hover:text-rose-700 transition-colors"
                                    onClick={() => onSelect({ id: supplier.id, name: supplier.lieferantenname })}
                                >
                                    {supplier.lieferantenname}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </Card>
        </div>
    );
};
