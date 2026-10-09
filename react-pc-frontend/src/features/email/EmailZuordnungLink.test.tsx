import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { EmailZuordnungLink } from './EmailZuordnungLink';
import type { EmailZuordnung } from './emailZuordnung';

const anfrage: EmailZuordnung = {
    art: 'Anfrage',
    label: 'Anfrage · Garagentor Mustermann',
    pfad: '/anfragen?anfrageId=12',
    titel: 'Anfrage · Garagentor Mustermann öffnen',
};

function Ort() {
    const ort = useLocation();
    return <p data-testid="ort">{ort.pathname + ort.search}</p>;
}

function renderInZeile(zuordnung: EmailZuordnung | null, variante: 'liste' | 'kopf' = 'liste') {
    const zeileGeklickt = vi.fn();
    const zeileGedrueckt = vi.fn();
    const zeileTaste = vi.fn();
    render(<MemoryRouter initialEntries={['/emails/inbox']}>
        <Routes>
            <Route path="*" element={<>
                <div onClick={zeileGeklickt} onPointerDown={zeileGedrueckt} onMouseDown={zeileGedrueckt} onKeyDown={zeileTaste}>
                    <EmailZuordnungLink zuordnung={zuordnung} variante={variante} />
                </div>
                <Ort />
            </>} />
        </Routes>
    </MemoryRouter>);
    return { zeileGeklickt, zeileGedrueckt, zeileTaste };
}

describe('EmailZuordnungLink', () => {
    it('rendert ohne Zuordnung nichts', () => {
        renderInZeile(null);
        expect(screen.queryByTestId('email-zuordnung')).not.toBeInTheDocument();
    });

    it('ist ein echter Link mit Ziel, Klartext und Tooltip', () => {
        renderInZeile(anfrage);
        const link = screen.getByRole('link', { name: 'Anfrage · Garagentor Mustermann öffnen' });
        expect(link).toHaveAttribute('href', '/anfragen?anfrageId=12');
        expect(link).toHaveAttribute('title', 'Anfrage · Garagentor Mustermann öffnen');
        expect(link).toHaveAttribute('draggable', 'false');
        expect(link).toHaveTextContent('Anfrage · Garagentor Mustermann');
    });

    it('navigiert beim Klick, ohne die Zeile auszuwählen', async () => {
        const { zeileGeklickt, zeileGedrueckt } = renderInZeile(anfrage);
        await userEvent.click(screen.getByRole('link'));
        expect(screen.getByTestId('ort')).toHaveTextContent('/anfragen?anfrageId=12');
        expect(zeileGeklickt).not.toHaveBeenCalled();
        expect(zeileGedrueckt).not.toHaveBeenCalled();
    });

    it('reicht Zeiger- und Tastenereignisse nicht an die ziehbare Zeile weiter', () => {
        const { zeileGedrueckt, zeileTaste } = renderInZeile(anfrage);
        const link = screen.getByRole('link');
        fireEvent.pointerDown(link);
        fireEvent.mouseDown(link);
        fireEvent.keyDown(link, { key: 'Enter' });
        expect(zeileGedrueckt).not.toHaveBeenCalled();
        expect(zeileTaste).not.toHaveBeenCalled();
    });

    it('zeigt eine Zuordnung ohne Ziel als Klartext ohne Link', () => {
        renderInZeile({ art: 'Steuerberater', label: 'Steuerberater', pfad: null, titel: 'Steuerberater' });
        expect(screen.queryByRole('link')).not.toBeInTheDocument();
        const chip = screen.getByTestId('email-zuordnung');
        expect(chip).toHaveTextContent('Steuerberater');
        expect(chip).toHaveClass('text-slate-600');
    });

    it('kürzt lange Namen in der Liste und behält den vollen Text im Tooltip', () => {
        const lang = { ...anfrage, label: `Anfrage · ${'Garagentor Mustermann '.repeat(6).trim()}` };
        renderInZeile({ ...lang, titel: `${lang.label} öffnen` });
        const link = screen.getByRole('link');
        expect(link).toHaveClass('max-w-[16rem]');
        expect(link.querySelector('.truncate')).toHaveTextContent(lang.label);
        expect(link).toHaveAttribute('title', `${lang.label} öffnen`);
    });

    it('nutzt im Mail-Kopf die volle Breite', () => {
        renderInZeile(anfrage, 'kopf');
        expect(screen.getByRole('link')).toHaveClass('max-w-full');
    });
});
