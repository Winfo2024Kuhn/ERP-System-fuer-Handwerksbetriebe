import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AddressAutocomplete, type AddressValue } from './AddressAutocomplete';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

const photonResponse = {
    features: [
        {
            properties: {
                street: 'Musterstraße',
                housenumber: '12',
                postcode: '30163',
                city: 'Hannover',
                country: 'Deutschland',
                countrycode: 'DE'
            }
        },
        {
            properties: {
                street: 'Musterstraße',
                housenumber: '12a',
                postcode: '30163',
                city: 'Hannover',
                country: 'Deutschland',
                countrycode: 'DE'
            }
        }
    ]
};

function renderComponent(initial: Partial<AddressValue> = {}) {
    const value: AddressValue = { strasse: '', plz: '', ort: '', ...initial };
    const onChange = vi.fn();
    const utils = render(<AddressAutocomplete value={value} onChange={onChange} />);
    return { ...utils, onChange };
}

describe('AddressAutocomplete', () => {
    beforeEach(() => {
        mockFetch.mockReset();
        mockFetch.mockResolvedValue({
            ok: true,
            json: async () => photonResponse
        } as Response);
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('rendert drei Eingabefelder (Straße, PLZ, Ort)', () => {
        renderComponent();
        expect(screen.getByLabelText('Straße')).toBeInTheDocument();
        expect(screen.getByLabelText('PLZ')).toBeInTheDocument();
        expect(screen.getByLabelText('Ort')).toBeInTheDocument();
    });

    it('zeigt initiale Werte an', () => {
        renderComponent({ strasse: 'Hauptstr. 1', plz: '12345', ort: 'Berlin' });
        expect(screen.getByLabelText('Straße')).toHaveValue('Hauptstr. 1');
        expect(screen.getByLabelText('PLZ')).toHaveValue('12345');
        expect(screen.getByLabelText('Ort')).toHaveValue('Berlin');
    });

    it('ruft onChange beim Tippen in Straßenfeld auf', async () => {
        const user = userEvent.setup();
        const { onChange } = renderComponent();
        await user.type(screen.getByLabelText('Straße'), 'A');
        expect(onChange).toHaveBeenCalledWith({ strasse: 'A', plz: '', ort: '' });
    });

    it('triggert keinen Fetch unter minChars (default 2)', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        renderComponent({ strasse: 'M' });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(500);
        });
        expect(mockFetch).not.toHaveBeenCalled();
    });

    it('ruft Photon-API nach Debounce auf', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        renderComponent({ strasse: 'Musterstraße 12' });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
        const url = mockFetch.mock.calls[0][0] as string;
        expect(url).toContain('photon.komoot.io');
        expect(url).toContain(encodeURIComponent('Musterstraße 12'));
    });

    it('zeigt Vorschläge im Dropdown nach erfolgreicher Suche', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        renderComponent({ strasse: 'Musterstraße' });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        await waitFor(() => expect(screen.getByText('Musterstraße 12')).toBeInTheDocument());
        expect(screen.getAllByText('30163 Hannover, Deutschland').length).toBeGreaterThan(0);
    });

    it('füllt alle drei Felder beim Auswählen eines Vorschlags', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const { onChange } = renderComponent({ strasse: 'Musterstraße' });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        await waitFor(() => expect(screen.getByText('Musterstraße 12')).toBeInTheDocument());

        vi.useRealTimers();
        const user = userEvent.setup();
        await user.click(screen.getByText('Musterstraße 12'));

        expect(onChange).toHaveBeenLastCalledWith({
            strasse: 'Musterstraße 12',
            plz: '30163',
            ort: 'Hannover'
        });
    });

    it('deaktiviert alle Felder bei disabled=true', () => {
        const onChange = vi.fn();
        render(
            <AddressAutocomplete
                value={{ strasse: '', plz: '', ort: '' }}
                onChange={onChange}
                disabled
            />
        );
        expect(screen.getByLabelText('Straße')).toBeDisabled();
        expect(screen.getByLabelText('PLZ')).toBeDisabled();
        expect(screen.getByLabelText('Ort')).toBeDisabled();
    });

    it('rendert ohne Labels wenn showLabels=false', () => {
        const onChange = vi.fn();
        render(
            <AddressAutocomplete
                value={{ strasse: '', plz: '', ort: '' }}
                onChange={onChange}
                showLabels={false}
            />
        );
        expect(screen.queryByText('Straße')).not.toBeInTheDocument();
        expect(screen.queryByText('PLZ')).not.toBeInTheDocument();
        expect(screen.queryByText('Ort')).not.toBeInTheDocument();
    });

    it('filtert Ergebnisse nach erlaubten Ländercodes', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        mockFetch.mockResolvedValue({
            ok: true,
            json: async () => ({
                features: [
                    { properties: { street: 'Foreign Rd', city: 'Paris', countrycode: 'FR' } },
                    { properties: { street: 'Hauptstr.', housenumber: '5', postcode: '10115', city: 'Berlin', countrycode: 'DE' } }
                ]
            })
        } as Response);

        renderComponent({ strasse: 'Hauptstr' });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(400);
        });
        await waitFor(() => expect(screen.getByText('Hauptstr. 5')).toBeInTheDocument());
        expect(screen.queryByText('Foreign Rd')).not.toBeInTheDocument();
    });
});
