/**
 * Speicherformat des E-Mail-Buttons: Der Editor muss den Button als
 * Outlook-taugliche Tabelle ausgeben und gespeichertes HTML wieder
 * einlesen. DSGVO: nur Beispieltext.
 */
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { Editor } from '@tiptap/core';
import { TiptapEditor } from '../../components/TiptapEditor';
import { ToastProvider } from '../../components/ui/toast';

const GESPEICHERT =
  '<p>Danke!</p><table data-email-button="" class="not-prose" role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:separate;margin:16px 0;"><tbody><tr><td bgcolor="#500010" style="border-radius:6px;"><a href="{{REVIEW_URL}}" target="_blank" rel="noopener noreferrer" style="display:inline-block;padding:13px 26px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:6px;">Jetzt Bewertung abgeben</a></td></tr></tbody></table>';

function editorAus(container: HTMLElement): Editor {
  const pm = container.querySelector('.ProseMirror') as (HTMLElement & { editor: Editor }) | null;
  if (!pm) throw new Error('.ProseMirror nicht gefunden');
  return pm.editor;
}

function zeige(value: string, onChange = vi.fn()) {
  return render(
    <ToastProvider>
      <TiptapEditor value={value} onChange={onChange} emailButtons />
    </ToastProvider>
  );
}

describe('E-Mail-Button im TiptapEditor', () => {
  it('liest gespeichertes Button-HTML ein und gibt es als Outlook-taugliche Tabelle wieder aus', async () => {
    const { container } = zeige(GESPEICHERT);

    // jsdom schreibt style-Attribute normalisiert zurück ("color: rgb(...)"),
    // daher Struktur statt exaktem String vergleichen.
    const ausgabe = document.createElement('div');
    ausgabe.innerHTML = editorAus(container).getHTML();
    const zelle = ausgabe.querySelector('table[data-email-button] td');
    const link = zelle?.querySelector('a');
    expect(zelle?.getAttribute('bgcolor')).toBe('#500010');
    expect(link?.getAttribute('href')).toBe('{{REVIEW_URL}}');
    expect(link?.textContent).toBe('Jetzt Bewertung abgeben');
    expect(await screen.findByText('Führt zum Google-Bewertungs-Link')).toBeInTheDocument();
  });

  it('verwirft gefährliche Link-Ziele beim Einlesen', () => {
    const { container } = zeige(GESPEICHERT.replace('{{REVIEW_URL}}', 'javascript:alert(1)'));

    expect(editorAus(container).getHTML()).toContain('href=""');
  });

  it('bietet den Button nur an, wenn emailButtons gesetzt ist', () => {
    const { queryByText } = render(<TiptapEditor value="<p>Hallo</p>" onChange={vi.fn()} />);

    expect(queryByText('Button')).not.toBeInTheDocument();
  });

  it('fügt über den Dialog einen Button mit eigener Adresse ein', () => {
    const onChange = vi.fn();
    const { container } = zeige('<p>Hallo</p>', onChange);

    fireEvent.click(screen.getByText('Button'));
    fireEvent.change(screen.getByLabelText('Beschriftung'), { target: { value: 'Zur Webseite' } });
    fireEvent.click(screen.getByRole('radio', { name: /Eigene Adresse/ }));
    fireEvent.change(screen.getByLabelText('Internet-Adresse'), { target: { value: 'www.beispiel.de' } });
    fireEvent.click(screen.getByRole('button', { name: 'Einfügen' }));

    const html = editorAus(container).getHTML();
    expect(html).toContain('data-email-button');
    expect(html).toContain('href="https://www.beispiel.de"');
    expect(html).toContain('>Zur Webseite</a>');
    expect(onChange).toHaveBeenCalled();
  });

  it('fügt ohne gültige Adresse nichts ein', () => {
    const { container } = zeige('<p>Hallo</p>');

    fireEvent.click(screen.getByText('Button'));
    fireEvent.click(screen.getByRole('radio', { name: /Eigene Adresse/ }));
    fireEvent.change(screen.getByLabelText('Internet-Adresse'), { target: { value: 'kein link' } });
    fireEvent.click(screen.getByRole('button', { name: 'Einfügen' }));

    expect(editorAus(container).getHTML()).not.toContain('data-email-button');
    expect(screen.getByText(/gültige Internet-Adresse/)).toBeInTheDocument();
  });

  it('ändert Beschriftung eines vorhandenen Buttons', async () => {
    const { container } = zeige(GESPEICHERT);

    fireEvent.click(await screen.findByRole('button', { name: /Ändern/ }));
    expect(screen.getByLabelText('Beschriftung')).toHaveValue('Jetzt Bewertung abgeben');
    fireEvent.change(screen.getByLabelText('Beschriftung'), { target: { value: 'Bewerten Sie uns' } });
    fireEvent.click(screen.getByRole('button', { name: 'Übernehmen' }));

    const html = editorAus(container).getHTML();
    expect(html).toContain('>Bewerten Sie uns</a>');
    expect(html).toContain('href="{{REVIEW_URL}}"');
  });

  it('entfernt einen Button', async () => {
    const { container } = zeige(GESPEICHERT);

    fireEvent.click(await screen.findByRole('button', { name: 'Button entfernen' }));

    expect(editorAus(container).getHTML()).toBe('<p>Danke!</p>');
  });
});
