import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { VersandVorschauDialog } from './VersandVorschauDialog';
import type { VersandVorschau } from './VersandVorschauDialog';
const vorschau: VersandVorschau={version:3,vorschauHash:'hash-3',subject:'Anfrage PA-3',htmlBody:'<p>Guten Tag</p><script>nicht zeigen</script>',empfaenger:'einkauf@example.test',pdfDateiId:1,anlageVersionIds:[8]};
describe('VersandVorschauDialog',()=>{it('zeigt Empfänger und Anlagen vor Freigabe und übergibt den Vorschauhash',async()=>{const user=userEvent.setup();const freigeben=vi.fn(async()=>{});render(<VersandVorschauDialog vorschau={vorschau} onFreigeben={freigeben} onSchliessen={()=>{}}/>);expect(screen.getByText('einkauf@example.test')).toBeInTheDocument();expect(screen.getByText(/1 freigegebene/)).toBeInTheDocument();expect(screen.queryByText(/nicht zeigen/)).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:/freigeben und senden/i}));expect(freigeben).toHaveBeenCalledWith('hash-3');});});
