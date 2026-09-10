import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { StundensatzEditModal } from './StundensatzEditModal';
import { ToastProvider } from './ui/toast';
import type { Arbeitsgang } from '../types';

describe('Stundensatz systemeigene Zahleneingabe', () => {
 it('leert Null beim Fokus, erhält Kommaentwurf und speichert die vollständige Zahl', async () => {
  const user=userEvent.setup();const save=vi.fn().mockResolvedValue(undefined);
  render(<ToastProvider><StundensatzEditModal arbeitsgang={{id:1,beschreibung:'Montage',stundensatz:0,stundensatzJahr:2026} as Arbeitsgang} isOpen onClose={vi.fn()} onSave={save}/></ToastProvider>);
  const input=screen.getByRole('textbox',{name:'Neuer Stundensatz (€/h)'});
  await user.click(input);expect(input).toHaveValue('');
  await user.type(input,'8,');await user.click(screen.getByRole('button',{name:'Speichern'}));expect(save).not.toHaveBeenCalled();expect(input).toHaveValue('8,');
  await user.type(input,'5');await user.click(screen.getByRole('button',{name:'Speichern'}));expect(save).toHaveBeenCalledWith(1,8.5);
 });
 it('erhält vorhandene Nichtnullwerte und blockiert leere Pflichtwerte',async()=>{
  const user=userEvent.setup();const save=vi.fn();
  render(<ToastProvider><StundensatzEditModal arbeitsgang={{id:1,beschreibung:'Montage',stundensatz:7.7,stundensatzJahr:2026} as Arbeitsgang} isOpen onClose={vi.fn()} onSave={save}/></ToastProvider>);
  const input=screen.getByRole('textbox',{name:'Neuer Stundensatz (€/h)'});expect(input).toHaveValue('7,7');
  await user.click(input);expect(input).toHaveValue('7,7');await user.clear(input);await user.click(screen.getByRole('button',{name:'Speichern'}));expect(save).not.toHaveBeenCalled();
 });
});
