import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CreateArticleModal } from './CreateArticleModal';
import { ToastProvider } from './ui/toast';

describe('CreateArticleModal', () => {
  it('leaves an unknown price absent and returns the saved article while keeping onSave compatible', async () => {
    const article = { id: 92, produktname: 'Quadratrohr', preis: null };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [] })
      .mockResolvedValueOnce({ ok: true, json: async () => article });
    vi.stubGlobal('fetch', fetchMock);
    const onCreated = vi.fn();
    const onSave = vi.fn();
    const onClose = vi.fn();
    render(<ToastProvider><CreateArticleModal onClose={onClose} onSave={onSave} onCreated={onCreated} /></ToastProvider>);
    fireEvent.change(screen.getByPlaceholderText('z.B. Quadratrohr 40x40x2'), { target: { value: 'Quadratrohr' } });
    fireEvent.click(screen.getByRole('button', { name: 'Artikel anlegen' }));
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(article));
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).not.toHaveProperty('preis');
    expect(onSave).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });
});
